/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.render.driver

import me.regadpole.plumbot.render.ScreenshotFormat

import me.regadpole.plumbot.render.RenderBudget

import com.microsoft.playwright.*
import com.microsoft.playwright.options.ScreenshotType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.BrowserDecision
import me.regadpole.plumbot.render.BrowserStrategy
import me.regadpole.plumbot.render.ImageRenderer
import me.regadpole.plumbot.render.JsonEncoder
import me.regadpole.plumbot.render.RenderLibraryLoader
import me.regadpole.plumbot.render.RenderOptions
import me.regadpole.plumbot.render.RenderSecurity
import me.regadpole.plumbot.render.RenderSecurity.toBrowserSafeUrl
import me.regadpole.plumbot.render.ThemeManager
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class PlaywrightImageRenderer(
    private val context: PlatformContext,
    themeManager: ThemeManager,
    private val libraryLoader: RenderLibraryLoader? = null
) : ImageRenderer {

    /** 主题管理器可被门户重载替换，因此不能是构造期固化的 `val`。 */
    @Volatile
    private var themeManager: ThemeManager = themeManager

    fun updateThemeManager(newThemeManager: ThemeManager) {
        themeManager = newThemeManager
    }

    /**
     * 字体注入 CSS（由门户在 `configure()` 时构建）。
     *
     * 用服主已为 AWT 配置的同一个 TTF 注入页面，使两条渲染途径**字形一致**并避免中文豆腐块。
     * 为 null 表示不注入（未配置字体 / 超过体积上限），此时页面回落到系统字体栈。
     */
    @Volatile
    private var fontCss: String? = null

    fun updateFontCss(css: String?) {
        fontCss = css
    }

    override val type: String = "playwright"

    /**
     * 真实探测：只有在运行期确实能加载到 Playwright 时才声明支持。
     *
     * 旧实现硬编码 `true`，导致 `engine=playwright` 时每次调用都先失败一次
     * （`NoClassDefFoundError`）再降级，同时把把失败伪装成成功。
     */
    override val isSupported: Boolean
        get() = runCatching { Class.forName("com.microsoft.playwright.Playwright") }.isSuccess

    private val isInitialized = AtomicBoolean(false)
    private val shutdownHookRegistered = AtomicBoolean(false)
    private val handshakeWarning = AtomicBoolean(false)

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var browserContext: BrowserContext? = null

    private class PooledPage(val page: Page, var useCount: Int = 0)
    private val pagePool = Channel<PooledPage>(3)

    private val renderDispatcher = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "PlumBot-Render-Worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    @Synchronized
    private fun ensureStarted() {
        if (isInitialized.get()) return

        // 装载门：先确保 Playwright 依赖已在运行期就位（Libby 按需下载）。
        libraryLoader?.let { loader ->
            if (!loader.ensurePlaywright()) {
                throw IllegalStateException("Playwright 依赖装载失败（离线或仓库不可达）")
            }
        }

        context.log(LogLevel.INFO, "[PlumBot-Render] 检测到服主启用了 Playwright 引擎，正在按需启动无头浏览器...")

        // 持久化目录与下载镜像必须在 Playwright.create() **之前**通过环境变量注入
        val pw = Playwright.create(Playwright.CreateOptions().setEnv(playwrightEnvironment()))

        // R25 / D6：沙箱**默认开启**。此前这里无条件传 `--no-sandbox`，等于这条防线从未生效。
        // 键名取反向语义（disable_sandbox）：键缺失时等于沙箱开启，属安全侧默认。
        val sandboxDisabled = context.config.getBoolean("render", "playwright", "disable_sandbox")
        if (sandboxDisabled) {
            context.log(LogLevel.WARN, PlaywrightSandbox.disabledWarning())
        }

        val launchOptions = BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(
                listOf(
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    // 依据冻结基线 D-9：头像改为服务端预取并合成 data URL 后，
                    // 浏览器不再需要跨域取图，故移除 --disable-web-security/--allow-file-access-from-files。
                    "--disable-crash-reporter",
                    "--disable-breakpad",
                    "--no-crash-upload",
                    "--mute-audio"
                ) + PlaywrightSandbox.args(sandboxDisabled)
            )

        applyBrowserDecision(launchOptions)

        val br = try {
            pw.chromium().launch(launchOptions)
        } catch (e: Throwable) {
            // 沙箱不可用（容器常见）时给出**明确指引**，但**不自动关沙箱** ——
            // 自动降级等于悄悄拆掉防线；失败会走既有的"降级为纯文本 + 计数"，不会静默无图。
            if (!sandboxDisabled && PlaywrightSandbox.looksLikeSandboxFailure(e.message)) {
                context.log(LogLevel.ERROR, PlaywrightSandbox.sandboxFailureHint())
            }
            throw e
        }

        br.onDisconnected {
            context.log(LogLevel.WARN, "[PlumBot-Render] 底层 Chromium 意外退出，看门狗回收资源并重置...")
            releaseAll()
            isInitialized.set(false)
        }

        val ctxOptions = Browser.NewContextOptions()
            .setDeviceScaleFactor(globalScaleFactor())
            // bypassCSP 保留：它并非跨域控制，仅为避免模板自带的 CSP 阻断内联脚本/样式而致白屏。
            .setBypassCSP(true)
        val bCtx = br.newContext(ctxOptions)

        val themeRoots = themeManager.allowedFileRoots
        val allowedHosts = resolveAllowedHosts()
        bCtx.route("**/*") { route ->
            val request = route.request()
            val url = request.url()
            if (isRequestAllowed(url, themeRoots, allowedHosts)) {
                route.resume()
            } else {
                // 仅对主文档记录日志，避免子资源被拦截时刷屏
                if (request.resourceType() == "document") {
                    context.log(LogLevel.WARN, "[PlumBot-Render] 已拦截越权请求：$url")
                }
                route.abort()
            }
        }

        playwright = pw
        browser = br
        browserContext = bCtx

        for (i in 0 until 2) {
            val page = bCtx.newPage()
            if (pagePool.trySend(PooledPage(page, 0)).isFailure) {
                // 池已满：显式关闭多余页面，避免永久泄漏
                context.log(LogLevel.WARN, "[PlumBot-Render] 页面池已满，关闭多余页面以避免泄漏。")
                runCatching { page.close() }
            }
        }

        if (shutdownHookRegistered.compareAndSet(false, true)) {
            // 只注册一次：旧实现每次 ensureStarted 都追加一个钩子，浏览器反复重启会导致钩子累积
            Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
        }
        isInitialized.set(true)
        context.log(LogLevel.INFO, "[PlumBot-Render] Playwright 高清渲染引擎就绪！")
    }

    /**
     * 请求放行判定（P1-9 / P1-10）。
     *
     * - `file:` → 必须落在**主题根目录**之内（旧实现放行整个数据目录，而那里存放着数据库口令）；
     * - `http(s):` → 主机白名单 **且** 解析后不得是内网/保留地址（旧实现用字符串黑名单，可被
     *   `http://2130706433/`、八进制、IPv6-mapped、指向内网的域名绕过）；
     * - `data:` / `blob:` / `about:` → 放行（服务端预取的头像以 data URL 交付）。
     */
    private fun isRequestAllowed(url: String, themeRoots: List<File>, allowedHosts: Set<String>): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return when (scheme) {
            "file" -> themeRoots.any { RenderSecurity.isFileUrlWithinRoot(url, it) }
            "data", "blob", "about" -> true
            "http", "https" -> {
                val host = RenderSecurity.hostOf(url)
                host != null &&
                    RenderSecurity.isHostAllowed(host, allowedHosts) &&
                    !RenderSecurity.resolvesToBlockedAddress(host)
            }
            else -> false
        }
    }

    /** 出口白名单：来自配置，缺省回落到基线 §3.4 的内置列表。 */
    private fun resolveAllowedHosts(): Set<String> = RenderSecurity.allowedHostsFrom(context.config)

    override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? = withContext(renderDispatcher) {
        ensureStarted()

        // 绝对截止时间：`options.timeoutMs` 是**整次渲染**的预算，不是每个阶段的预算。
        // 旧实现把它逐阶段重复计满（等池 + 导航 + 握手 + READY + 截图），
        // 实际总耗时可达配置值的数倍，调用方的"超时"形同虚设。
        val budget = RenderBudget(options.timeoutMs)
        fun remaining(): Double = budget.remaining()

        val theme = context.config.getString("render", "playwright", "theme") ?: "default"
        val entryFile = themeManager.resolveThemeEntry(theme, viewRoute)
        if (entryFile == null || !entryFile.isFile || entryFile.length() == 0L) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 主题入口不可用，跳过本次 Playwright 渲染。")
            return@withContext null
        }
        val entryUrl = entryFile.toBrowserSafeUrl()

        // 页面池等待必须有界：池被耗尽时旧实现会永久挂起，并占满全部渲染线程
        val pooled = withTimeoutOrNull(remaining().toLong()) { pagePool.receive() }
        if (pooled == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 页面池等待超时（${options.timeoutMs}ms），跳过本次 Playwright 渲染。")
            return@withContext null
        }

        val page = pooled.page
        var pageHealthy = true

        try {
            // 原生超时：协程的 withTimeout 无法中断 navigate/waitForFunction 这类阻塞调用，
            // 旧实现实际由 Playwright 自身的 30s 默认超时支配，等同于假超时。
            // 这里每阶段都取**剩余预算**，保证总耗时不超过 options.timeoutMs。
            page.setDefaultTimeout(remaining())
            page.setDefaultNavigationTimeout(remaining())

            val payloadJson = JsonEncoder.encode(payload)
            page.navigate("$entryUrl#/$viewRoute", Page.NavigateOptions().setTimeout(remaining()))

            // 协议握手：模板声明的协议版本与它实际实现的 route。
            // 不匹配时直接降级，而不是先注入数据再等超时（旧行为会白等一整个导航超时）。
            when (val handshake = readManifest(page, viewRoute)) {
                Handshake.Missing -> Unit // 旧模板未声明 manifest：保持兼容，继续渲染
                Handshake.ProtocolMismatch -> return@withContext null
                Handshake.RouteUnsupported -> return@withContext null
                Handshake.Ok -> Unit
            }

            page.evaluate(
                """
                (data) => {
                    window.__PLUMBOT_DATA__ = JSON.parse(data);
                    window.__PLUMBOT_RENDER_READY__ = false;
                    window.dispatchEvent(new Event('plumbot-render-request'));
                }
                """.trimIndent(),
                payloadJson
            )

            page.waitForFunction(
                "() => window.__PLUMBOT_RENDER_READY__ === true",
                null,
                Page.WaitForFunctionOptions().setTimeout(remaining())
            )

            // 字体注入必须在 READY 之后：模板自身的样式已就位，注入的 !important 才能稳定覆盖。
            // 失败不影响出图（只是可能缺字），因此单独容错。
            fontCss?.let { css ->
                runCatching { page.addStyleTag(Page.AddStyleTagOptions().setContent(css)) }
                    .onFailure { context.log(LogLevel.WARN, "[PlumBot-Render] 字体注入失败：${it.message}") }
            }

            // 输出格式：默认 PNG（文字最清晰）；可切 JPEG 档以显著减小体积。
            // 注意 JPEG **没有 alpha 通道**，因此 omitBackground 必须保持 false，
            // 否则透明区域会被填成黑色（模板留白处会变黑块）。
            val format = ScreenshotFormat.normalize(
                context.config.getString("render", "playwright", "image_format")
            )
            val screenshotOptions = Locator.ScreenshotOptions()
                .setType(if (format == ScreenshotFormat.JPEG) ScreenshotType.JPEG else ScreenshotType.PNG)
                .setOmitBackground(false)
                .setTimeout(remaining())

            if (format == ScreenshotFormat.JPEG) {
                val configured = context.config.getInteger("render", "playwright", "jpeg_quality")
                screenshotOptions.setQuality(ScreenshotFormat.quality(configured))
            }

            val target = page.locator("#render-target")
            target.screenshot(screenshotOptions)
        } catch (e: Throwable) {
            pageHealthy = false
            withContext(NonCancellable) {
                runCatching { page.close() }
                replenishPool(1)
            }
            throw e
        } finally {
            if (pageHealthy) {
                withContext(NonCancellable) {
                    runCatching { page.evaluate("() => { localStorage.clear(); sessionStorage.clear(); }") }
                    if (pooled.useCount >= 200) {
                        runCatching { page.close() }
                        replenishPool(1)
                    } else {
                        pooled.useCount++
                        if (pagePool.trySend(pooled).isFailure) {
                            runCatching { page.close() }
                        }
                    }
                }
            }
        }
    }

    /**
     * 补充页面池。任何失败只告警，绝不让池被永久缩容（否则渲染能力会静默退化），
     * 也绝不吞掉调用方的原始异常。
     */
    private fun replenishPool(count: Int) {
        val ctx = browserContext ?: return
        repeat(count) {
            runCatching { ctx.newPage() }
                .onSuccess { page ->
                    if (pagePool.trySend(PooledPage(page, 0)).isFailure) {
                        runCatching { page.close() }
                    }
                }
                .onFailure {
                    context.log(LogLevel.WARN, "[PlumBot-Render] 重建页面失败: ${it.message}")
                }
        }
    }

    override fun shutdown() {
        if (!isInitialized.compareAndSet(true, false)) return
        releaseAll()
    }

    /** 协议握手结果。 */
    private enum class Handshake { Ok, Missing, ProtocolMismatch, RouteUnsupported }

    /**
     * Playwright 进程环境变量。
     *
     * - `PLAYWRIGHT_BROWSERS_PATH`：把浏览器二进制固定落进插件数据目录，避免写进
     *   `~/.cache/ms-playwright` 或 `%USERPROFILE%\AppData\Local\ms-playwright`（不可控、不可卸载、可能爆盘）；
     * - `PLAYWRIGHT_DOWNLOAD_HOST`：浏览器 CDN 镜像（国内网络）。
     *
     * 注意：`install --only-shell` 只能在**手动预装**时通过 CLI 传入（见 README），
     * 无法通过环境变量生效，因此自动下载兜底会取完整 Chromium。
     */
    private fun playwrightEnvironment(): Map<String, String> {
        val config = context.config
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val env = mutableMapOf(
            "PLAYWRIGHT_BROWSERS_PATH" to BrowserStrategy.resolveBrowsersPath(
                config.getString("render", "playwright", "browsers_path"),
                pluginFolder
            )
        )
        BrowserStrategy.resolveDownloadHost(
            config.getString("render", "playwright", "download_host")
        )?.let { env["PLAYWRIGHT_DOWNLOAD_HOST"] = it }
        return env
    }

    /** 应用三级获取链：配置指定 → 探测系统 → 自动下载。 */
    private fun applyBrowserDecision(launchOptions: BrowserType.LaunchOptions) {
        val config = context.config
        val decision = BrowserStrategy.decide(
            customPath = config.getString("render", "playwright", "custom_chrome_path"),
            explicitChannel = config.getString("render", "playwright", "browser_channel"),
            detectedChannel = BrowserStrategy.detectSystemBrowser()
        )

        when (decision) {
            is BrowserDecision.Executable -> {
                launchOptions.setExecutablePath(Path.of(decision.path))
                context.log(LogLevel.INFO, "[PlumBot-Render] 使用配置指定的浏览器：${decision.path}")
            }

            is BrowserDecision.Channel -> {
                launchOptions.setChannel(decision.channel)
                context.log(LogLevel.INFO, "[PlumBot-Render] 复用系统浏览器通道：${decision.channel}")
            }

            is BrowserDecision.AutoDownload -> {
                context.log(
                    LogLevel.INFO,
                    "[PlumBot-Render] 未指定且未探测到系统浏览器，将由 Playwright 自动下载 Chromium" +
                        "（首次约数百 MB；可按 README 的手动预装路径用 --only-shell 减小体积）。"
                )
            }
        }
        decision.note?.let { context.log(LogLevel.WARN, "[PlumBot-Render] $it") }
    }

    /**
     * 读取模板声明的 manifest 并校验协议版本与 route 可用性。
     *
     * 旧模板未声明 manifest 时返回 [Handshake.Missing]（保持兼容）；
     * 不匹配只告警一次，避免每次渲染都刷屏。
     */
    private fun readManifest(page: Page, viewRoute: String): Handshake {
        val raw = runCatching { page.evaluate("() => window.__PLUMBOT_MANIFEST__ || null") }.getOrNull()
            ?: return Handshake.Missing
        val manifest = raw as? Map<*, *> ?: return Handshake.Missing

        val declaredProtocol = (manifest["protocol"] as? Number)?.toInt()
        val expectedProtocol = context.config
            .getInteger("render", "playwright", "template_protocol")
            .let { if (it <= 0) EXPECTED_PROTOCOL else it }

        if (declaredProtocol != null && declaredProtocol != expectedProtocol) {
            warnOnce("模板协议版本 $declaredProtocol 与期望的 $expectedProtocol 不匹配，已跳过 Playwright 渲染。")
            return Handshake.ProtocolMismatch
        }

        val declaredRoutes = (manifest["routes"] as? List<*>)?.filterIsInstance<String>()
        if (!declaredRoutes.isNullOrEmpty() && viewRoute !in declaredRoutes) {
            warnOnce("模板未实现 route '$viewRoute'（已实现：$declaredRoutes），直接降级。")
            return Handshake.RouteUnsupported
        }
        return Handshake.Ok
    }

    private fun warnOnce(message: String) {
        if (handshakeWarning.compareAndSet(false, true)) {
            context.log(LogLevel.WARN, "[PlumBot-Render] $message")
        }
    }

    /**
     * 回收全部原生资源（页面池 → 浏览器上下文 → 浏览器 → Playwright 驱动）。
     *
     * 看门狗与正常关闭共用同一实现：旧实现在 Chromium 意外退出时**只置了一个标志**，
     * 旧的 `browser` / `context` / 页面池全部残留，反复崩溃会持续泄漏 Chromium 与 node 驱动进程。
     * 每一步都容错 —— 浏览器已死时 `close()` 必然抛异常。
     */
    private fun releaseAll() {
        var page = pagePool.tryReceive().getOrNull()
        while (page != null) {
            runCatching { page.page.close() }
            page = pagePool.tryReceive().getOrNull()
        }
        runCatching { browserContext?.close() }
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
        browserContext = null
        browser = null
        playwright = null
    }

    private companion object {
        /** 与 `render-protocol.json` 的 protocol 字段保持一致。 */
        const val EXPECTED_PROTOCOL = 1

        /**
         * 设备像素比默认值。
         *
         * 决策（图片体积优化）：**默认从 2.0 降到 1.5** —— 像素数按面积减少约 44%，
         * 字节数近似同比例下降，而手机 QQ 里几乎看不出差别。
         * 需要极致清晰度的用户可以自行调回 2.0（上限 4.0）。
         */
        const val DEFAULT_SCALE_FACTOR = 1.5
        const val MIN_SCALE_FACTOR = 1.0
        const val MAX_SCALE_FACTOR = 4.0
    }

    /**
     * 全局设备像素比（Retina 2x）。
     *
     * 它在**浏览器上下文创建时**生效，因此只能全局配置：
     * `render.playwright.scale_factor`（1.0 ~ 4.0，支持 1.5 这类小数）。
     */
    private fun globalScaleFactor(): Double {
        val configured = context.config.getDouble("render", "playwright", "scale_factor")
        if (configured.isFinite() && configured in MIN_SCALE_FACTOR..MAX_SCALE_FACTOR) return configured
        if (configured.isFinite() && configured > 0.0) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] scale_factor=$configured 超出 $MIN_SCALE_FACTOR~$MAX_SCALE_FACTOR 区间，已回落 $DEFAULT_SCALE_FACTOR"
            )
        }
        return DEFAULT_SCALE_FACTOR
    }
}
