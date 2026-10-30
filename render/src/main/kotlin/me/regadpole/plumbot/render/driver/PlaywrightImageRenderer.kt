// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.driver

import com.microsoft.playwright.*
import com.microsoft.playwright.options.ScreenshotAnimations
import com.microsoft.playwright.options.ScreenshotType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.BrowserDecision
import me.regadpole.plumbot.render.BrowserStrategy
import me.regadpole.plumbot.render.ImageRenderer
import me.regadpole.plumbot.render.JsonEncoder
import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightInstallGate
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightInstaller
import me.regadpole.plumbot.render.PlaywrightRenderException
import me.regadpole.plumbot.render.PlaywrightShell
import me.regadpole.plumbot.render.RenderBudget
import me.regadpole.plumbot.render.RenderOptions
import me.regadpole.plumbot.render.RenderSecurity
import me.regadpole.plumbot.render.RenderSecurity.toBrowserSafeUrl
import me.regadpole.plumbot.render.ScreenshotFormat
import me.regadpole.plumbot.render.ThemeFailureKind
import me.regadpole.plumbot.render.ThemeManager
import me.regadpole.plumbot.render.ThemeResolution
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Playwright 渲染驱动。
 *
 * 硬约束：
 * - create() 之前固定注入 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1：Playwright Java 1.49 的
 *   DriverJar 会在 create() 内无条件执行 node cli.js install（全量 chromium/firefox/webkit，
 *   最长 10 分钟），是"首次渲染卡住、超时后整引擎不可用"的根因；
 * - 浏览器只来自 BrowserStrategy 的判定：custom_chrome_path、品牌 channel、系统浏览器、
 *   playwright-browsers 下的已安装版本。没有第五种"隐式下载"；
 * - 任一阶段失败都抛 PlaywrightRenderException（code/stage/route/detail/hint/命令），
 *   不吞异常、不返回 null；
 * - 调用 create() 时把 TCCL 切到插件类加载器，规避 DriverJar 用 TCCL 查找 driver/ 资源
 *   在 Libby 注入场景下失败。
 */
internal class PlaywrightImageRenderer(
    private val context: PlatformContext,
    themeManager: ThemeManager,
    /**
     * 装载门。**只用来"按一下按钮"**：[com.microsoft.playwright.Playwright] 不在类路径时，
     * 本类只调用 [PlaywrightInstallGate.startOnce] 把装载排进平台异步泳道，随后立刻抛
     * [PlaywrightErrorCodes.DEPENDENCY_MISSING] 交给 AWT 兜底。
     * 这里绝不能同步装载：真实下载可达数分钟，会把渲染线程和页面池一起卡死。
     */
    private val installGate: PlaywrightInstallGate? = null
) : ImageRenderer {

    /**
     * 模板 manifest 声明的可渲染路由。
     *
     * - `null`：还没读到 manifest（尚未成功握手）—— 见 [supportsRoute]；
     * - 空集：模板没写 `routes` 字段，视为**不限制**任何路由；
     * - 非空：只认这些路由。
     *
     * 由 [verifyManifest] 在每次握手时刷新，因此渲染过一帧之后就能早期判定，
     * 不必让调用方先触发一次失败降级（`fallback_to_awt` 会把失败画成 AWT 文字图，看不出问题）。
     */
    @Volatile
    private var templateRoutes: Set<String>? = null

    /** 主题管理器可被门户重载替换，因此不能是构造期固化的 val。 */
    @Volatile
    private var themeManager: ThemeManager = themeManager

    fun updateThemeManager(newThemeManager: ThemeManager) {
        themeManager = newThemeManager
        // 主题可能换了另一份模板构建，旧的 routes 声明不再可信；置空后退回"未知"，
        // 让调用方按原顺序再探一次（只多探一次，不会长期退化）。
        templateRoutes = null
    }

    /** 字体注入 CSS；null 表示不注入。 */
    @Volatile
    private var fontCss: String? = null

    fun updateFontCss(css: String?) {
        fontCss = css
    }

    override val type: String = "playwright"

    /** 真实探测：运行期确实能加载到 Playwright 才声明支持。 */
    override val isSupported: Boolean
        get() = isPlaywrightPresent()

    private val isInitialized = AtomicBoolean(false)
    private val shutdownHookRegistered = AtomicBoolean(false)

    /** JVM 关闭钩子（用于在插件禁用时主动摘除，见 [removeShutdownHookIfNeeded]）。 */
    @Volatile
    private var shutdownHook: Thread? = null

    /**
     * 是否是**我们自己**在关浏览器（shutdown / reload 回收 / 关服钩子）。
     *
     * `onDisconnected` 对主动 close 和进程崩溃给出同一个回调，没有这个标记时
     * 每次 reload 与关服都会打一条"底层 Chromium 意外退出"的假故障。
     */
    @Volatile
    private var closingInternally = false

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var browserContext: BrowserContext? = null

    private class PooledPage(val page: Page, var useCount: Int = 0)

    /**
     * 页面池容量固定为 [PAGE_POOL_SIZE]。
     *
     * **只有前台标签页能可靠截图**：Chromium 的后台标签页不产生新的合成帧，
     * `Page.captureScreenshot` 会一直等帧直到超时（实测后台页 5s 必超时，
     * `bringToFront()` 之后百毫秒内返回）。所以池里只留 1 个页面：它始终是前台页，
     * 顺带把 Playwright 渲染串行化 —— Playwright Java 的 API 不是线程安全的，
     * 多页并发还会抛 `Object doesn't exist: response@...`。
     */
    private val pagePool = Channel<PooledPage>(PAGE_POOL_SIZE)

    /**
     * 渲染协程泳道。并发 2 是给 AWT 兜底与排队留的余量，Playwright 侧复用单页，
     * 真正出图仍是串行的。
     */
    private val renderDispatcher = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "PlumBot-Render-Worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** 安装器只用于生成手动命令，不在这里执行下载。 */
    private val installer: PlaywrightInstaller by lazy {
        PlaywrightInstaller(PlaywrightImageRenderer::class.java.classLoader) { message ->
            context.log(LogLevel.INFO, "[PlumBot-Render] " + message)
        }
    }

    @Synchronized
    private fun ensureStarted() {
        if (isInitialized.get()) return

        // DriverJar.getDriverResourceURI() 用的是 Thread.currentThread().contextClassLoader。
        // Libby 把 playwright 注入插件加载器后，工作线程的 TCCL 可能仍是服务端加载器，
        // 这里显式切到本类（插件）加载器，create() 结束后恢复。
        val previousTccl = Thread.currentThread().contextClassLoader
        val ownLoader = PlaywrightImageRenderer::class.java.classLoader
        if (ownLoader != null) {
            Thread.currentThread().contextClassLoader = ownLoader
        }
        try {
            startEngine()
        } finally {
            if (previousTccl != null) {
                Thread.currentThread().contextClassLoader = previousTccl
            }
        }
    }

    private fun startEngine() {
        if (!isPlaywrightPresent()) {
            // **绝不在渲染线程内联下载**：这里只"按一下"装载门就立刻失败，
            // 真正的下载在平台异步泳道上进行，完成后下一次出图自动改用高清引擎。
            val requested = installGate?.startOnce("render") == true
            val state = if (requested) {
                "已按需在后台开始装载，本轮先用 AWT 兜底，装载完成后自动改用 Playwright 引擎。"
            } else {
                "装载未在本次触发（已就绪 / 正在装载 / 失败冷却中），执行 /plumbot render doctor 查看原因。"
            }
            throw renderFailure(
                PlaywrightErrorCodes.DEPENDENCY_MISSING,
                "dependency",
                null,
                "Playwright 运行库尚未装载：com.microsoft.playwright.Playwright 不在类路径。$state",
                "确认服务器可访问 Maven 仓库并已下载 playwright 1.49.0；离线环境请手动放入 lib 目录后执行 /plumbot render doctor。",
                manualInstallCommand(installOptions())
            )
        }

        val options = installOptions()
        val decision = resolveBrowserDecision(options)
        val env = playwrightEnvironment(options)
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 启用 Playwright 引擎，浏览器来源：" + describeDecision(decision)
        )

        val pw = try {
            Playwright.create(Playwright.CreateOptions().setEnv(env))
        } catch (e: Throwable) {
            throw renderFailure(
                PlaywrightErrorCodes.DRIVER_START_FAILED,
                "driver",
                null,
                "Playwright.create() 失败：" + (e.message ?: e::class.java.simpleName),
                "检查 driver-bundle 是否被 Libby 注入、临时目录是否可写可执行；本插件已把 TCCL 切到插件加载器。执行 /plumbot render doctor 查看 jar 路径。",
                null,
                e
            )
        }

        var launchedBrowser: Browser? = null
        var createdContext: BrowserContext? = null
        try {
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
                        "--disable-crash-reporter",
                        "--disable-breakpad",
                        "--no-crash-upload",
                        "--mute-audio"
                    ) + PlaywrightSandbox.args(sandboxDisabled)
                )

            when (decision) {
                is BrowserDecision.Executable -> {
                    launchOptions.setExecutablePath(Path.of(decision.path))
                    context.log(LogLevel.INFO, "[PlumBot-Render] 使用浏览器可执行文件（" + decision.source + "）：" + decision.path)
                }

                is BrowserDecision.Channel -> {
                    launchOptions.setChannel(decision.channel)
                    context.log(LogLevel.INFO, "[PlumBot-Render] 使用浏览器品牌通道：" + decision.channel)
                }

                is BrowserDecision.InvalidConfig -> configFailure(decision.detail)
                is BrowserDecision.Missing -> browserMissing(options, decision.detail)
            }

            val browserInstance = try {
                pw.chromium().launch(launchOptions)
            } catch (e: Throwable) {
                if (!sandboxDisabled && PlaywrightSandbox.looksLikeSandboxFailure(e.message)) {
                    context.log(LogLevel.ERROR, PlaywrightSandbox.sandboxFailureHint())
                }
                throw renderFailure(
                    PlaywrightErrorCodes.LAUNCH_FAILED,
                    "launch",
                    null,
                    "Chromium 启动失败：" + (e.message ?: e::class.java.simpleName),
                    "确认可执行文件确实是 Chrome/Edge；容器内如确需关闭沙箱，可设 render.playwright.disable_sandbox=true（不推荐），并优先使用 custom_chrome_path 或系统浏览器。",
                    null,
                    e
                )
            }
            launchedBrowser = browserInstance

            browserInstance.onDisconnected {
                // 旧实例的迟到回调（重建后 browser 已指向新实例）直接忽略。
                if (browser !== browserInstance) return@onDisconnected
                if (closingInternally || !isInitialized.get()) {
                    // 主动关闭：资源已由 shutdown()/releaseAll() 回收，这里不再重复回收。
                    context.log(LogLevel.INFO, "[PlumBot-Render] Chromium 已按请求关闭（主动释放资源）。")
                    return@onDisconnected
                }
                context.log(LogLevel.WARN, "[PlumBot-Render] 底层 Chromium 意外退出，回收资源并重置。")
                releaseAll()
                isInitialized.set(false)
            }

            val ctxOptions = Browser.NewContextOptions()
                .setDeviceScaleFactor(globalScaleFactor())
                .setBypassCSP(true)

            val browserCtx = try {
                browserInstance.newContext(ctxOptions)
            } catch (e: Throwable) {
                throw renderFailure(
                    PlaywrightErrorCodes.LAUNCH_FAILED,
                    "context",
                    null,
                    "创建浏览器上下文失败：" + (e.message ?: e::class.java.simpleName),
                    "降低 render.playwright.scale_factor 后重试；若持续失败请执行 /plumbot render doctor。",
                    null,
                    e
                )
            }
            createdContext = browserCtx

            val themeRoots = themeManager.allowedFileRoots
            val allowedHosts = resolveAllowedHosts()
            try {
                browserCtx.route("**/*") { route ->
                    val request = route.request()
                    val url = request.url()
                    if (isRequestAllowed(url, themeRoots, allowedHosts)) {
                        route.resume()
                    } else {
                        if (request.resourceType() == "document") {
                            context.log(LogLevel.WARN, "[PlumBot-Render] 已拦截越权请求：" + url)
                        }
                        route.abort()
                    }
                }
            } catch (e: Throwable) {
                throw renderFailure(
                    PlaywrightErrorCodes.LAUNCH_FAILED,
                    "context",
                    null,
                    "注册请求拦截规则失败：" + (e.message ?: e::class.java.simpleName),
                    "检查 render.allowed_hosts 配置是否为字符串列表。",
                    null,
                    e
                )
            }

            for (i in 0 until PAGE_POOL_SIZE) {
                val page = browserCtx.newPage()
                if (pagePool.trySend(PooledPage(page, 0)).isFailure) {
                    context.log(LogLevel.WARN, "[PlumBot-Render] 页面池已满，关闭多余页面以避免泄漏。")
                    runCatching { page.close() }
                }
            }

            playwright = pw
            browser = browserInstance
            browserContext = browserCtx

            if (shutdownHookRegistered.compareAndSet(false, true)) {
                val hook = Thread({ shutdown() }, "PlumBot-Render-ShutdownHook")
                shutdownHook = hook
                runCatching { Runtime.getRuntime().addShutdownHook(hook) }
            }
            // 新引擎就绪：复位"主动关闭"标记，让后续真实断连仍然报警。
            closingInternally = false
            isInitialized.set(true)
            context.log(LogLevel.INFO, "[PlumBot-Render] Playwright 高清渲染引擎就绪！")
        } catch (e: Throwable) {
            runCatching { createdContext?.close() }
            runCatching { launchedBrowser?.close() }
            runCatching { pw.close() }
            throw e
        }
    }

    /**
     * 请求放行判定。
     *
     * - file: 必须落在主题根目录内；
     * - http(s): 只按主机名白名单放行，不做 DNS 解析后的内网/保留地址判定；
     * - data/blob/about: 放行（服务端预取的头像以 data URL 交付）。
     */
    private fun isRequestAllowed(url: String, themeRoots: List<File>, allowedHosts: Set<String>): Boolean {
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false
        return when (scheme) {
            "file" -> themeRoots.any { RenderSecurity.isFileUrlWithinRoot(url, it) }
            "data", "blob", "about" -> true
            "http", "https" -> {
                val host = RenderSecurity.hostOf(url)
                host != null && RenderSecurity.isHostAllowed(host, allowedHosts)
            }
            else -> false
        }
    }

    /** 出口白名单：来自配置，缺省回落到基线 §3.4 的内置列表。 */
    private fun resolveAllowedHosts(): Set<String> = RenderSecurity.allowedHostsFrom(context.config)

    override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? = withContext(renderDispatcher) {
        ensureStarted()

        // 绝对截止时间：options.timeoutMs 是整次渲染的预算，不是每阶段预算。
        val budget = RenderBudget(options.timeoutMs)
        fun remaining(): Double = budget.remaining()

        val theme = context.config.getString("render", "playwright", "theme")?.takeIf { it.isNotBlank() } ?: "default"
        val resolution = try {
            themeManager.resolveTheme(theme, viewRoute)
        } catch (e: Throwable) {
            throw renderFailure(
                PlaywrightErrorCodes.TEMPLATE_MISSING,
                "template",
                viewRoute,
                "解析主题入口失败：" + (e.message ?: e::class.java.simpleName),
                templateHint(),
                templateManualCommand(theme),
                e
            )
        }

        val entryFile = when (resolution) {
            is ThemeResolution.Available -> resolution.file
            is ThemeResolution.Failed -> {
                val code = if (resolution.kind == ThemeFailureKind.MISSING) {
                    PlaywrightErrorCodes.TEMPLATE_MISSING
                } else {
                    PlaywrightErrorCodes.TEMPLATE_INVALID
                }
                throw renderFailure(
                    code,
                    "template",
                    viewRoute,
                    "模板不可用（" + resolution.kind + "）：" + resolution.detail,
                    templateHint(),
                    templateManualCommand(theme)
                )
            }
        }

        if (!entryFile.isFile || entryFile.length() == 0L) {
            throw renderFailure(
                PlaywrightErrorCodes.TEMPLATE_MISSING,
                "template",
                viewRoute,
                "模板入口文件缺失或为空：" + entryFile.absolutePath,
                templateHint(),
                templateManualCommand(theme)
            )
        }

        val entryUrl = try {
            entryFile.toBrowserSafeUrl()
        } catch (e: Throwable) {
            throw renderFailure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                "template",
                viewRoute,
                "模板入口无法转换为浏览器安全 URL：" + (e.message ?: e::class.java.simpleName),
                templateHint(),
                templateManualCommand(theme),
                e
            )
        }

        val pooled = withTimeoutOrNull(remaining().toLong().coerceAtLeast(1L)) { pagePool.receive() }
            ?: throw renderFailure(
                PlaywrightErrorCodes.RENDER_TIMEOUT,
                "pool",
                viewRoute,
                "页面池等待超时（" + options.timeoutMs + "ms）",
                "提高 render.playwright.render_timeout_ms，或降低并发渲染数量。",
                null
            )

        val page = pooled.page
        var pageHealthy = true
        var stage = "payload"

        try {
            val payloadJson = JsonEncoder.encode(payload)

            page.setDefaultTimeout(remaining())
            page.setDefaultNavigationTimeout(remaining())

            stage = "navigate"
            page.navigate("$entryUrl#/$viewRoute", Page.NavigateOptions().setTimeout(remaining()))

            stage = "handshake"
            when (val manifest = verifyManifest(page, viewRoute)) {
                is ManifestCheck.Ok -> Unit
                is ManifestCheck.Invalid -> throw renderFailure(
                    PlaywrightErrorCodes.TEMPLATE_INVALID,
                    "handshake",
                    viewRoute,
                    manifest.detail,
                    "模板必须是 protocol=1 的单文件构建，并在挂载前调用 installManifest() 写入 window.__PLUMBOT_MANIFEST__。",
                    templateManualCommand(theme)
                )
            }

            stage = "evaluate"
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

            stage = "ready"
            page.waitForFunction(
                "() => window.__PLUMBOT_RENDER_READY__ === true",
                null,
                Page.WaitForFunctionOptions().setTimeout(remaining())
            )

            // 字体注入必须在 READY 之后：模板自身样式已就位，注入的 !important 才能稳定覆盖。
            fontCss?.let { css ->
                runCatching { page.addStyleTag(Page.AddStyleTagOptions().setContent(css)) }
                    .onFailure { context.log(LogLevel.WARN, "[PlumBot-Render] 字体注入失败：" + it.message) }
            }

            // 不用 Locator.screenshot：元素截图会先 scrollIntoView，再"等两帧几何完全一致"，
            // READY 之后任何持续回流（异步字体、图片解码、滚动条出现）都会把这段等待拖到超时
            // （线上实测 8959ms 后报 "Timeout ... waiting for element to be stable"，整次渲染降级 AWT）。
            // 改为：等字体就绪 + 连续两次几何采样一致 → page.screenshot(clip) 直接截该矩形。
            // 截图前必须把页面激活到前台：后台标签页不产帧，page.screenshot 会静默等到超时
            // （日志里 Call log 停在 "fonts loaded" 之后再无步骤）。池里只有一个页面时它本就是
            // 前台页，这里再做一次是兜底：reload / 页面重建后激活状态不一定可依赖。
            stage = "activate"
            runCatching { page.bringToFront() }.onFailure {
                context.log(LogLevel.WARN, "[PlumBot-Render] 激活页面失败（继续尝试截图）：" + it.message)
            }

            stage = "settle"
            val target = settleRenderTarget(page, viewRoute)
            val targetDiagnostics = target.diagnose()

            val format = ScreenshotFormat.normalize(
                context.config.getString("render", "playwright", "image_format")
            )
            val screenshotOptions = Page.ScreenshotOptions()
                .setType(if (format == ScreenshotFormat.JPEG) ScreenshotType.JPEG else ScreenshotType.PNG)
                .setOmitBackground(false)
                // 动画会让裁剪矩形内的内容持续变化；禁用后截图结果可复现
                .setAnimations(ScreenshotAnimations.DISABLED)
                .setClip(target.x, target.y, target.width, target.height)
                .setTimeout(remaining())

            if (format == ScreenshotFormat.JPEG) {
                screenshotOptions.setQuality(
                    ScreenshotFormat.quality(context.config.getInteger("render", "playwright", "jpeg_quality"))
                )
            }

            stage = "screenshot"
            val bytes = try {
                page.screenshot(screenshotOptions)
            } catch (e: PlaywrightException) {
                // 把失败时的页面现场写进 detail：没有它只能反复猜"为什么截图超时"
                throw renderFailure(
                    PlaywrightErrorCodes.SCREENSHOT_FAILED,
                    "screenshot",
                    viewRoute,
                    "截图失败：" + (e.message ?: e::class.java.simpleName) + "；现场 " + targetDiagnostics,
                    "若为 TimeoutError：先看现场里的 tab=，hidden 说明页面没被激活到前台（后台标签页不产帧，截图会等到超时）；tab=visible 才需怀疑 READY 之后仍在回流（异步字体/图片），可增大 render_timeout_ms。",
                    templateManualCommand(theme),
                    e
                )
            }
            if (bytes.isEmpty()) {
                throw renderFailure(
                    PlaywrightErrorCodes.SCREENSHOT_FAILED,
                    "screenshot",
                    viewRoute,
                    "截图返回空字节（#render-target 可能不可见或尺寸为 0）；现场 " + targetDiagnostics,
                    "确认模板存在 id=render-target 且可见；必要时提高 render_timeout_ms。",
                    null
                )
            }
            bytes
        } catch (e: PlaywrightRenderException) {
            pageHealthy = false
            withContext(NonCancellable) {
                runCatching { page.close() }
                replenishPool(1)
            }
            throw e
        } catch (e: CancellationException) {
            pageHealthy = false
            withContext(NonCancellable) {
                runCatching { page.close() }
                replenishPool(1)
            }
            throw e
        } catch (e: Throwable) {
            pageHealthy = false
            withContext(NonCancellable) {
                runCatching { page.close() }
                replenishPool(1)
            }
            throw mapStageFailure(stage, viewRoute, theme, e)
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
     * `#render-target` 的裁剪矩形（CSS 像素，相对页面左上角）与现场诊断。
     *
     * 为什么需要 `y` 带 `scrollY`：`page.screenshot(clip)` 会让 Playwright 以
     * `captureBeyondViewport` 方式截取，clip 坐标是**文档坐标**而非视口坐标。
     */
    private data class RenderTargetBox(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double,
        val fontsReady: Boolean,
        val diagnostics: String
    ) {
        fun sameBox(other: RenderTargetBox): Boolean =
            x == other.x && y == other.y && width == other.width && height == other.height

        fun diagnose(): String = diagnostics
    }

    /**
     * 等 `#render-target` 布局稳定，并返回可交给 `page.screenshot(clip)` 的矩形。
     *
     * 稳定性判据：连续两次采样（间隔 [SETTLE_INTERVAL_MS]）几何完全一致，且文档字体
     * 已是 `loaded` 状态。总等待上限 [SETTLE_TIMEOUT_MS]，超时也返回当前几何 ——
     * 让随后的截图超时给出更准确的错误，而不是在这里盲等。
     */
    private suspend fun settleRenderTarget(page: Page, viewRoute: String): RenderTargetBox {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS
        var previous: RenderTargetBox? = null
        while (true) {
            val current = sampleRenderTarget(page, viewRoute)
            if (current.width <= MIN_TARGET_SIZE || current.height <= MIN_TARGET_SIZE) {
                // 尺寸为 0：模板还没把内容挂上 DOM，继续等（0 尺寸不算"稳定"）
                previous = null
            } else if (current.fontsReady && previous != null && previous.sameBox(current)) {
                return current
            } else {
                previous = current
            }
            if (System.currentTimeMillis() >= deadline) {
                context.log(
                    LogLevel.DEBUG,
                    "[PlumBot-Render] #render-target 在 " + SETTLE_TIMEOUT_MS + "ms 内未判定为稳定，按当前几何截图：" + current.diagnose()
                )
                return current
            }
            delay(SETTLE_INTERVAL_MS)
        }
    }

    /** 采样一次 `#render-target` 的几何与页面状态（供 [settleRenderTarget] 与失败诊断）。 */
    private fun sampleRenderTarget(page: Page, viewRoute: String): RenderTargetBox {
        val raw: Any? = page.evaluate(
            """
            () => {
                const el = document.querySelector('#render-target');
                if (!el) {
                    return { missing: true };
                }
                const r = el.getBoundingClientRect();
                const cs = getComputedStyle(el);
                return {
                    missing: false,
                    x: r.left + window.scrollX,
                    y: r.top + window.scrollY,
                    width: r.width,
                    height: r.height,
                    scrollY: window.scrollY,
                    viewport: window.innerWidth + 'x' + window.innerHeight,
                    docHeight: document.documentElement.scrollHeight,
                    fontsReady: !document.fonts || document.fonts.status === 'loaded',
                    animations: document.getAnimations ? document.getAnimations().length : 0,
                    display: cs.display,
                    visibility: cs.visibility,
                    opacity: cs.opacity,
                    tab: document.visibilityState
                };
            }
            """.trimIndent()
        )
        val map = raw as? Map<*, *> ?: return RenderTargetBox(
            0.0, 0.0, 0.0, 0.0, false,
            "无法读取 #render-target 几何（evaluate 返回 " + (raw?.javaClass?.simpleName ?: "null") + "）"
        )
        if (map["missing"] == true) {
            throw renderFailure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                "settle",
                viewRoute,
                "模板没有渲染出 #render-target 元素（window.__PLUMBOT_RENDER_READY__ 已为 true，但 DOM 中不存在该 id）",
                "模板必须在数据就绪后渲染 id=render-target 的根节点；协议需为 1。",
                null
            )
        }
        fun num(key: String): Double = (map[key] as? Number)?.toDouble() ?: 0.0
        val box = RenderTargetBox(
            x = num("x"),
            y = num("y"),
            width = num("width"),
            height = num("height"),
            fontsReady = map["fontsReady"] == true,
            diagnostics = "box=" + fmtMetric(num("width")) + "x" + fmtMetric(num("height")) +
                "@" + fmtMetric(num("x")) + "," + fmtMetric(num("y")) +
                " scrollY=" + fmtMetric(num("scrollY")) +
                " viewport=" + map["viewport"] +
                " docHeight=" + fmtMetric(num("docHeight")) +
                " fonts=" + (if (map["fontsReady"] == true) "loaded" else "loading") +
                " animations=" + num("animations").toInt() +
                " display=" + map["display"] +
                " visibility=" + map["visibility"] +
                " opacity=" + map["opacity"] +
                " tab=" + map["tab"]
        )
        return box
    }

    private fun fmtMetric(value: Double): String =
        String.format(java.util.Locale.US, "%.1f", value)

    /** 补充页面池。任何失败只告警，绝不让池被永久缩容。 */
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
                    context.log(LogLevel.WARN, "[PlumBot-Render] 重建页面失败：" + it.message)
                }
        }
    }

    /**
     * 引擎是否确信模板实现了 [route]。
     *
     * 只回答**已经读到**的 manifest：第一次握手之前返回 `null`（未知），
     * 让调用方按原顺序尝试；握手之后旧模板会稳定返回 `false`，头像卡据此直接走兼容路由。
     */
    override fun supportsRoute(route: String): Boolean? {
        val declared = templateRoutes ?: return null
        return declared.isEmpty() || route in declared
    }

    /**
     * 关闭引擎。幂等；重量级清理的线程策略由调用方决定
     * （[ImageRenderManager][me.regadpole.plumbot.render.ImageRenderManager] 会把它丢到回收线程）。
     */
    override fun shutdown() {
        // 先标记"主动关闭"，再改状态：onDisconnected 立刻据此把这次断连判为预期内。
        closingInternally = true
        // 模板声明属于当前引擎实例：换主题/重载后由新的 manifest 重新学习。
        templateRoutes = null
        if (!isInitialized.compareAndSet(true, false)) return
        releaseAll()
        removeShutdownHookIfNeeded()
    }

    /**
     * 摘除本引擎注册的 JVM 关闭钩子（插件被禁用/重载时）。
     *
     * 必须摘：钩子持有引擎引用并会在 JVM 退出时调用 [shutdown]；插件类加载器一旦关闭，
     * 钩子再去 `PlaywrightImpl.close()` 就会抛
     * "The plugin classloader for PlumBot has thrown a zip file error."。
     * 关服时钩子自己正在执行，不能移除自身 —— 此时直接跳过。
     */
    private fun removeShutdownHookIfNeeded() {
        val hook = shutdownHook ?: return
        if (Thread.currentThread() === hook) return
        shutdownHook = null
        runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
    }

    /** manifest 校验结果。 */
    private sealed interface ManifestCheck {
        data object Ok : ManifestCheck
        data class Invalid(val detail: String) : ManifestCheck
    }

    /**
     * 读取并校验模板 manifest。
     *
     * 缺少 manifest、缺少 protocol、协议不一致、route 未实现都算 PW_TEMPLATE_INVALID，
     * 不以"旧模板兼容"为名继续注入数据并等到超时。
     */
    private fun verifyManifest(page: Page, viewRoute: String): ManifestCheck {
        val raw = try {
            page.evaluate("() => window.__PLUMBOT_MANIFEST__ || null")
        } catch (e: Throwable) {
            return ManifestCheck.Invalid("读取 window.__PLUMBOT_MANIFEST__ 失败：" + (e.message ?: e::class.java.simpleName))
        }
        val manifest = raw as? Map<*, *>
            ?: return ManifestCheck.Invalid("模板未声明 window.__PLUMBOT_MANIFEST__（前端 SDK 的 installManifest 未执行）")

        val declaredProtocol = (manifest["protocol"] as? Number)?.toInt()
            ?: return ManifestCheck.Invalid("manifest 缺少 protocol 字段（模板 SDK 版本过旧或构建被裁剪）")

        val expectedProtocol = context.config
            .getInteger("render", "playwright", "template_protocol")
            .let { if (it <= 0) EXPECTED_PROTOCOL else it }

        if (declaredProtocol != expectedProtocol) {
            return ManifestCheck.Invalid(
                "模板协议版本 " + declaredProtocol + " 与插件期望的 " + expectedProtocol + " 不匹配"
            )
        }

        val declaredRoutes = (manifest["routes"] as? List<*>)?.filterIsInstance<String>()
        // 记录给 supportsRoute()：调用方（如头像卡）要靠它在**下次渲染之前**选对路由。
        // 缺 routes 字段按"不限制"记为空集，与下面的放行逻辑保持一致。
        templateRoutes = declaredRoutes?.toSet() ?: emptySet()
        if (!declaredRoutes.isNullOrEmpty() && viewRoute !in declaredRoutes) {
            return ManifestCheck.Invalid(
                "模板未实现 route '" + viewRoute + "'（已实现：" + declaredRoutes.joinToString(",") + "）"
            )
        }
        return ManifestCheck.Ok
    }

    /**
     * Playwright 进程环境变量。
     *
     * - PLAYWRIGHT_BROWSERS_PATH: 浏览器二进制固定在插件数据目录，便于卸载与诊断；
     * - PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1: 关闭 create() 内的无条件 install（首帧卡死的根因）；
     * - PLAYWRIGHT_DOWNLOAD_HOST: 只给显式安装器/手动命令使用，渲染进程不下载。
     */
    private fun playwrightEnvironment(options: PlaywrightInstallOptions): Map<String, String> {
        val env = mutableMapOf(
            "PLAYWRIGHT_BROWSERS_PATH" to options.browsersPath,
            "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1"
        )
        options.downloadHost?.let { env["PLAYWRIGHT_DOWNLOAD_HOST"] = it }
        return env
    }

    private fun installOptions(): PlaywrightInstallOptions {
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val browsersPath = BrowserStrategy.resolveBrowsersPath(
            context.config.getString("render", "playwright", "browsers_path"),
            pluginFolder
        )
        val configuredTimeout = context.config.getInteger("render", "playwright", "install_timeout_seconds")
        val timeoutSeconds = if (configuredTimeout > 0) configuredTimeout else DEFAULT_INSTALL_TIMEOUT_SECONDS
        val rawShell = context.config.getString("render", "playwright", "install_shell")
        val shell = PlaywrightShell.parse(rawShell)
            ?: configFailure(
                "render.playwright.install_shell='" + (rawShell ?: "") + "' 非法；可选值：no-shell / only-shell / full"
            )

        return PlaywrightInstallOptions(
            browsersPath = File(browsersPath).absolutePath,
            downloadHost = BrowserStrategy.resolveDownloadHost(
                context.config.getString("render", "playwright", "download_host")
            ),
            timeoutSeconds = timeoutSeconds,
            shell = shell,
            customChromePath = context.config.getString("render", "playwright", "custom_chrome_path"),
            browserChannel = context.config.getString("render", "playwright", "browser_channel"),
            osName = System.getProperty("os.name").orEmpty()
        )
    }

    /** 选择顺序（无自动下载）：browser_channel → custom_chrome_path → 系统浏览器 → 安装器管理的 playwright-browsers。 */
    private fun resolveBrowserDecision(options: PlaywrightInstallOptions): BrowserDecision {
        val detectedPath = BrowserStrategy.detectSystemBrowserPath(options.osName)
        val managedPath = BrowserStrategy.findManagedBrowser(options.browsersPath, options.osName)
        return BrowserStrategy.decide(
            customPath = options.customChromePath,
            explicitChannel = options.browserChannel,
            detectedPath = detectedPath,
            managedPath = managedPath
        )
    }

    private fun describeDecision(decision: BrowserDecision): String = when (decision) {
        is BrowserDecision.Executable -> decision.path + "（" + decision.source + "）"
        is BrowserDecision.Channel -> "channel=" + decision.channel
        is BrowserDecision.InvalidConfig -> "配置非法"
        is BrowserDecision.Missing -> "未找到浏览器"
    }

    private fun isPlaywrightPresent(): Boolean = runCatching {
        val loader = PlaywrightImageRenderer::class.java.classLoader
        if (loader != null) {
            Class.forName("com.microsoft.playwright.Playwright", false, loader)
        } else {
            Class.forName("com.microsoft.playwright.Playwright")
        }
    }.isSuccess

    private fun manualInstallCommand(options: PlaywrightInstallOptions): String? =
        runCatching { installer.manualCommand(options) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun templateManualCommand(theme: String): String? =
        runCatching { themeManager.manualTemplateCommand(theme) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun configFailure(detail: String): Nothing = throw renderFailure(
        PlaywrightErrorCodes.CONFIG_INVALID,
        "config",
        null,
        detail,
        "检查 config.yml 的 render.playwright.*；custom_chrome_path 与 browser_channel 互斥，channel 仅支持 chrome/msedge 家族。"
    )

    private fun browserMissing(options: PlaywrightInstallOptions, detail: String): Nothing = throw renderFailure(
        PlaywrightErrorCodes.BROWSER_MISSING,
        "browser",
        null,
        "未找到可用的 Chromium/Chrome/Edge，且 playwright-browsers 下没有已安装版本：" + detail,
        "运行 /plumbot render install 安装内置 Chromium；或设置 render.playwright.custom_chrome_path 指向 Chrome/Edge；或设置 browser_channel=chrome/msedge。渲染不再自动下载浏览器。",
        manualInstallCommand(options)
    )

    private fun templateHint(): String =
        "模板不随插件分发。请按手动命令把单文件模板放到插件数据目录 web/default/index.html，或配置 render.playwright.dev_web_dir 指向本地模板。"

    private fun mapStageFailure(stage: String, viewRoute: String, theme: String, cause: Throwable): PlaywrightRenderException {
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        return when (stage) {
            "navigate" -> renderFailure(
                PlaywrightErrorCodes.NAVIGATION_FAILED,
                stage,
                viewRoute,
                "导航到模板失败：" + detail,
                "检查入口文件是否存在、route 是否在模板中实现；若日志出现已拦截越权请求，请把资源域名加入 render.allowed_hosts。",
                null,
                cause
            )

            "handshake", "evaluate", "ready", "payload", "template" -> renderFailure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                stage,
                viewRoute,
                "模板握手/就绪阶段失败：" + detail,
                "模板需在数据就绪后把 window.__PLUMBOT_RENDER_READY__ 置为 true，并渲染 #render-target；协议需为 1。",
                templateManualCommand(theme),
                cause
            )

            "screenshot", "settle", "activate" -> renderFailure(
                PlaywrightErrorCodes.SCREENSHOT_FAILED,
                stage,
                viewRoute,
                "截图失败：" + detail,
                "确认 #render-target 存在且可见，并确认页面已在前台（后台标签页不产帧，截图会超时）；必要时提高 render.playwright.render_timeout_ms。",
                null,
                cause
            )

            else -> renderFailure(
                PlaywrightErrorCodes.NAVIGATION_FAILED,
                stage,
                viewRoute,
                "渲染失败：" + detail,
                "执行 /plumbot render doctor 并查看服务端日志。",
                null,
                cause
            )
        }
    }

    private fun renderFailure(
        code: String,
        stage: String,
        route: String?,
        detail: String,
        hint: String? = null,
        command: String? = null,
        cause: Throwable? = null
    ): PlaywrightRenderException = PlaywrightRenderException(code, stage, detail, hint, command, route, cause)

    /**
     * 回收全部原生资源（页面池 → 上下文 → 浏览器 → 驱动）。
     *
     * **先摘引用、再关闭**：并发渲染线程拿到 null 会直接降级为文本，
     * 而不是在已经关掉的浏览器上继续操作。
     *
     * 每一步都在**独立守护线程**上执行并带超时：`Browser.close()` 与
     * `PlaywrightImpl.close()` 会同步等待 node 驱动进程（`ProcessImpl.waitFor()`），
     * 实测单次 2~3s。调用者是主线程（reload / 禁用 / 关服）或 Chromium 断连回调线程，
     * 都不该被这段等待拖住；驱动若真卡死也只放弃等待并告警，daemon 线程不会挂住 JVM 退出。
     */
    private fun releaseAll() {
        val pooledPages = mutableListOf<Page>()
        var pooled = pagePool.tryReceive().getOrNull()
        while (pooled != null) {
            pooledPages += pooled.page
            pooled = pagePool.tryReceive().getOrNull()
        }
        val closingContext = browserContext
        val closingBrowser = browser
        val closingPlaywright = playwright
        browserContext = null
        browser = null
        playwright = null

        closeWithin("pages", PAGE_CLOSE_TIMEOUT_MS) { pooledPages.forEach { runCatching { it.close() } } }
        closeWithin("browser-context", CONTEXT_CLOSE_TIMEOUT_MS) { closingContext?.close() }
        closeWithin("browser", BROWSER_CLOSE_TIMEOUT_MS) { closingBrowser?.close() }
        closeWithin("driver", DRIVER_CLOSE_TIMEOUT_MS) { closingPlaywright?.close() }
    }

    /**
     * 在一次性守护线程上执行一步 close，最多等 [timeoutMillis]。
     *
     * 复制 TCCL 是必须的：Playwright 的驱动类与回调用 TCCL 解析资源，在 Libby 注入
     * 场景下工作线程的 TCCL 可能不是插件加载器。
     */
    private fun closeWithin(label: String, timeoutMillis: Long, block: () -> Unit) {
        val tccl = Thread.currentThread().contextClassLoader
        val worker = Thread({
            runCatching { block() }.onFailure { t ->
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] 关闭 " + label + " 失败：" + (t.message ?: t.javaClass.simpleName)
                )
            }
        }, "PlumBot-Render-Close-" + label).apply {
            isDaemon = true
            if (tccl != null) contextClassLoader = tccl
        }
        worker.start()
        worker.join(timeoutMillis)
        if (worker.isAlive) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 关闭 " + label + " 超时（" + timeoutMillis + "ms），已放弃等待；" +
                    "Chromium 驱动进程会随 JVM 退出被回收。"
            )
        }
    }

    private companion object {
        /** 与 render-protocol.json 的 protocol 字段保持一致。 */
        const val EXPECTED_PROTOCOL = 1

        const val DEFAULT_SCALE_FACTOR = 1.5
        const val MIN_SCALE_FACTOR = 1.0
        const val MAX_SCALE_FACTOR = 4.0
        const val DEFAULT_INSTALL_TIMEOUT_SECONDS = 600

        /**
         * `#render-target` 稳定性等待的上限与采样间隔。
         *
         * 取值理由：模板自身的 READY 信号通常已等过两帧（约 33ms），这里再补 1.5s 足够覆盖
         * 异步字体/图片回流；50ms 的采样间隔足以识别"还在动"。两者都不与整体渲染预算竞争
         * —— 超时后照常按当前几何截图，由截图超时给出真实错误。
         */
        const val SETTLE_TIMEOUT_MS = 1_500L
        const val SETTLE_INTERVAL_MS = 50L
        const val MIN_TARGET_SIZE = 1.0

        /**
         * 页面池大小。**必须保持 1**：
         *
         * 1. Chromium 只给前台标签页出合成帧，后台页 `page.screenshot` 必然等到超时；
         * 2. Playwright Java 的 API 非线程安全，多页并发会抛 `Object doesn't exist`；
         * 3. 单页把出图串行化，第二个请求在页面池上排队而不是互抢前台 ——
         *    并发 2 是给 AWT 兜底与排队留的余量，Playwright 侧始终复用这一页。
         *
         * 需要更高吞吐时调大排队深度（[me.regadpole.plumbot.render.ImageRenderManager]
         * 的 QUEUE_DEPTH），而不是加页面。
         */
        const val PAGE_POOL_SIZE = 1

        /**
         * 回收各阶段的等待上限。
         *
         * 取值来自实测：`Browser.close()` 约 2~3s（含等驱动回包）、`PlaywrightImpl.close()`
         * 约 2.4s（`Process.waitFor()`）。留足正常关闭的余量，只拦截真正卡死的驱动。
         */
        const val PAGE_CLOSE_TIMEOUT_MS = 5_000L
        const val CONTEXT_CLOSE_TIMEOUT_MS = 5_000L
        const val BROWSER_CLOSE_TIMEOUT_MS = 10_000L
        const val DRIVER_CLOSE_TIMEOUT_MS = 15_000L
    }

    /**
     * 浏览器上下文的设备像素比（device_scale_factor），在创建上下文时生效。
     * 取自 render.playwright.scale_factor（1.0 ~ 4.0，支持 1.5 这类小数）。
     */
    private fun globalScaleFactor(): Double {
        val configured = context.config.getDouble("render", "playwright", "scale_factor")
        if (configured.isFinite() && configured in MIN_SCALE_FACTOR..MAX_SCALE_FACTOR) return configured
        if (configured.isFinite() && configured > 0.0) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] scale_factor=" + configured + " 超出 " + MIN_SCALE_FACTOR + "~" + MAX_SCALE_FACTOR + " 区间，已回落 " + DEFAULT_SCALE_FACTOR
            )
        }
        return DEFAULT_SCALE_FACTOR
    }
}
