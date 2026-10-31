// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import com.microsoft.playwright.Page
import com.microsoft.playwright.PlaywrightException
import com.microsoft.playwright.options.ScreenshotAnimations
import com.microsoft.playwright.options.ScreenshotType
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightRenderException
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.RenderOptions
import me.regadpole.plumbot.render.engine.playwright.startup.EngineStartup
import me.regadpole.plumbot.render.install.PlaywrightInstallGate
import me.regadpole.plumbot.render.pipeline.RenderBudget
import me.regadpole.plumbot.render.support.JsonEncoder
import me.regadpole.plumbot.render.support.RenderSecurity.toBrowserSafeUrl
import me.regadpole.plumbot.render.theme.ThemeFailureKind
import me.regadpole.plumbot.render.theme.ThemeManager
import me.regadpole.plumbot.render.theme.ThemeResolution

/**
 * Playwright 渲染驱动：只保留类骨架与 render() 主流程。
 *
 * 硬约束（由拆出的协作者共同守护）：
 * - [EngineLifecycle] 在 `create()` 之前固定注入 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1：
 *   Playwright Java 1.49 的 DriverJar 会在 create() 内无条件执行 node cli.js install
 *   （全量 chromium/firefox/webkit，最长 10 分钟），是"首次渲染卡住、超时后整引擎不可用"的根因；
 * - 浏览器只来自 BrowserStrategy 的判定（见 [EngineLifecycle]）。没有第五种"隐式下载"；
 * - 任一阶段失败都抛 [PlaywrightRenderException]（code/stage/route/detail/hint/命令），
 *   不吞异常、不返回 null —— 阶段到错误码的映射见 [FailureMapper]；
 * - 出口白名单在 [EngineLifecycle] 注册的 request 拦截里执行；
 * - 整次渲染共享一个 [RenderBudget] 绝对截止时间，页面池等待用 withTimeoutOrNull 语义（见 [PagePool]）。
 *
 * 编排关系：
 * - [PagePool]：页面池 + 渲染泳道，只负责页面的生命；
 * - [EngineLifecycle]：引擎启动/关停与原生资源；
 * - [TargetSettler]：`#render-target` 稳定化与裁剪矩形；
 * - [ManifestVerifier]：模板 manifest 握手与 routes 声明；
 * - [FailureMapper]：阶段失败到错误码的映射。
 */
internal class PlaywrightImageRenderer(
    private val context: PlatformContext,
    themeManager: ThemeManager,
    /**
     * 装载门。**只用来"按一下按钮"**：[com.microsoft.playwright.Playwright] 不在类路径时，
     * 本类只让 [EngineLifecycle] 调用 [PlaywrightInstallGate.startOnce] 把装载排进平台异步泳道，
     * 随后立刻抛 [PlaywrightErrorCodes.DEPENDENCY_MISSING] 交给 AWT 兜底。
     * 这里绝不能同步装载：真实下载可达数分钟，会把渲染线程和页面池一起卡死。
     */
    private val installGate: PlaywrightInstallGate? = null
) : ImageRenderer {

    /** 主题管理器可被门户重载替换，因此不能是构造期固化的 val。 */
    @Volatile
    private var themeManager: ThemeManager = themeManager

    /** 字体注入 CSS；null 表示不注入。 */
    @Volatile
    private var fontCss: String? = null

    private val failureMapper = FailureMapper { themeManager }
    private val startup = EngineStartup(context, failureMapper)
    private val manifestVerifier = ManifestVerifier(context)
    private val targetSettler = TargetSettler(context, failureMapper)
    private val pagePool = PagePool(context)

    /**
     * 引擎生命周期。延迟构造是因为它反向读取 [pagePool] 与 [manifestVerifier]：
     * 二者只在渲染/关停时才需要，构造期没有循环依赖。
     */
    private val lifecycle: EngineLifecycle by lazy {
        EngineLifecycle(
            context = context,
            installGate = installGate,
            pagePool = pagePool,
            failureMapper = failureMapper,
            startup = startup,
            themeManager = { themeManager },
            onShutdown = { manifestVerifier.reset() }
        )
    }

    fun updateThemeManager(newThemeManager: ThemeManager) {
        themeManager = newThemeManager
        // 主题可能换了另一份模板构建，旧的 routes 声明不再可信；置空后退回"未知"，
        // 让调用方按原顺序再探一次（只多探一次，不会长期退化）。
        manifestVerifier.reset()
    }

    fun updateFontCss(css: String?) {
        fontCss = css
    }

    override val type: String = "playwright"

    /** 真实探测：运行期确实能加载到 Playwright 才声明支持。 */
    override val isSupported: Boolean
        get() = lifecycle.isPlaywrightPresent()

    /**
     * 引擎是否确信模板实现了 [route]。
     *
     * 只回答**已经读到**的 manifest：第一次握手之前返回 `null`（未知），
     * 让调用方按原顺序尝试；握手之后旧模板会稳定返回 `false`，头像卡据此直接走兼容路由。
     */
    override fun supportsRoute(route: String): Boolean? {
        val declared = manifestVerifier.templateRoutes ?: return null
        return declared.isEmpty() || route in declared
    }

    /**
     * 关闭引擎。幂等；重量级清理的线程策略由调用方决定
     * （[me.regadpole.plumbot.render.pipeline.ImageRenderManager] 会把它丢到回收线程）。
     */
    override fun shutdown() {
        lifecycle.shutdown()
    }

    override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? =
        withContext(pagePool.dispatcher) {
            lifecycle.ensureStarted()

            // 绝对截止时间：options.timeoutMs 是整次渲染的预算，不是每阶段预算。
            val budget = RenderBudget(options.timeoutMs)
            val theme = themeName()
            val entryUrl = resolveTemplateEntry(theme, viewRoute)
            val pooled = acquirePage(viewRoute, options.timeoutMs, budget)
            val stage = RenderStage()
            var pageHealthy = true
            try {
                preparePage(pooled.page, entryUrl, payload, budget, viewRoute, theme, stage)
                capture(pooled.page, budget, viewRoute, theme, stage)
            } catch (e: PlaywrightRenderException) {
                pageHealthy = false
                pagePool.discard(pooled, lifecycle.currentBrowserContext)
                throw e
            } catch (e: CancellationException) {
                pageHealthy = false
                pagePool.discard(pooled, lifecycle.currentBrowserContext)
                throw e
            } catch (e: Throwable) {
                pageHealthy = false
                pagePool.discard(pooled, lifecycle.currentBrowserContext)
                throw failureMapper.mapStageFailure(stage.value, viewRoute, theme, e)
            } finally {
                if (pageHealthy) {
                    pagePool.release(pooled, lifecycle.currentBrowserContext)
                }
            }
        }

    private fun themeName(): String =
        context.config.getString("render", "playwright", "theme")?.takeIf { it.isNotBlank() } ?: "default"

    /** 解析主题入口并转成浏览器安全 URL；模板缺失/非法都在这里映射成固定错误码。 */
    private suspend fun resolveTemplateEntry(theme: String, viewRoute: String): String {
        val resolution = try {
            themeManager.resolveTheme(theme, viewRoute)
        } catch (e: Throwable) {
            throw failureMapper.failure(
                PlaywrightErrorCodes.TEMPLATE_MISSING,
                "template",
                viewRoute,
                "解析主题入口失败：" + (e.message ?: e::class.java.simpleName),
                failureMapper.templateHint(),
                failureMapper.templateManualCommand(theme),
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
                throw failureMapper.failure(
                    code,
                    "template",
                    viewRoute,
                    "模板不可用（" + resolution.kind + "）：" + resolution.detail,
                    failureMapper.templateHint(),
                    failureMapper.templateManualCommand(theme)
                )
            }
        }

        if (!entryFile.isFile || entryFile.length() == 0L) {
            throw failureMapper.failure(
                PlaywrightErrorCodes.TEMPLATE_MISSING,
                "template",
                viewRoute,
                "模板入口文件缺失或为空：" + entryFile.absolutePath,
                failureMapper.templateHint(),
                failureMapper.templateManualCommand(theme)
            )
        }

        return try {
            entryFile.toBrowserSafeUrl()
        } catch (e: Throwable) {
            throw failureMapper.failure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                "template",
                viewRoute,
                "模板入口无法转换为浏览器安全 URL：" + (e.message ?: e::class.java.simpleName),
                failureMapper.templateHint(),
                failureMapper.templateManualCommand(theme),
                e
            )
        }
    }

    /**
     * 从页面池取页；等待的是**剩余渲染预算**，超时映射成 PW_RENDER_TIMEOUT。
     */
    private suspend fun acquirePage(
        viewRoute: String,
        timeoutMs: Long,
        budget: RenderBudget
    ): PagePool.PooledPage = pagePool.acquire(budget.remainingMs()) ?: throw failureMapper.failure(
        PlaywrightErrorCodes.RENDER_TIMEOUT,
        "pool",
        viewRoute,
        "页面池等待超时（" + timeoutMs + "ms）",
        "提高 render.playwright.render_timeout_ms，或降低并发渲染数量。",
        null
    )

    /** payload → 导航 → manifest 握手 → READY，之后再注入字体。 */
    private fun preparePage(
        page: Page,
        entryUrl: String,
        payload: Any,
        budget: RenderBudget,
        viewRoute: String,
        theme: String,
        stage: RenderStage
    ) {
        stage.value = "payload"
        val payloadJson = JsonEncoder.encode(payload)

        page.setDefaultTimeout(budget.remaining())
        page.setDefaultNavigationTimeout(budget.remaining())

        stage.value = "navigate"
        page.navigate("$entryUrl#/$viewRoute", Page.NavigateOptions().setTimeout(budget.remaining()))

        stage.value = "handshake"
        when (val manifest = manifestVerifier.verify(page, viewRoute)) {
            is ManifestCheck.Ok -> Unit
            is ManifestCheck.Invalid -> throw failureMapper.failure(
                PlaywrightErrorCodes.TEMPLATE_INVALID,
                "handshake",
                viewRoute,
                manifest.detail,
                "模板必须是 protocol=1 的单文件构建，并在挂载前调用 installManifest() 写入 window.__PLUMBOT_MANIFEST__。",
                failureMapper.templateManualCommand(theme)
            )
        }

        awaitReady(page, payloadJson, budget, stage)
    }

    /** 注入数据并等模板把 READY 置真；字体注入必须在 READY 之后。 */
    private fun awaitReady(page: Page, payloadJson: String, budget: RenderBudget, stage: RenderStage) {
        stage.value = "evaluate"
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

        stage.value = "ready"
        page.waitForFunction(
            "() => window.__PLUMBOT_RENDER_READY__ === true",
            null,
            Page.WaitForFunctionOptions().setTimeout(budget.remaining())
        )

        // 字体注入必须在 READY 之后：模板自身样式已就位，注入的 !important 才能稳定覆盖。
        fontCss?.let { css ->
            runCatching { page.addStyleTag(Page.AddStyleTagOptions().setContent(css)) }
                .onFailure { context.log(LogLevel.WARN, "[PlumBot-Render] 字体注入失败：" + it.message) }
        }
    }

    /**
     * 激活页面 → 等目标稳定 → 截取裁剪矩形。
     *
     * 不用 Locator.screenshot：元素截图会先 scrollIntoView，再"等两帧几何完全一致"，
     * READY 之后任何持续回流（异步字体、图片解码、滚动条出现）都会把这段等待拖到超时
     * （线上实测 8959ms 后报 "Timeout ... waiting for element to be stable"，整次渲染降级 AWT）。
     */
    private suspend fun capture(
        page: Page,
        budget: RenderBudget,
        viewRoute: String,
        theme: String,
        stage: RenderStage
    ): ByteArray {
        // 截图前必须把页面激活到前台：后台标签页不产帧，page.screenshot 会静默等到超时
        // （日志里 Call log 停在 "fonts loaded" 之后再无步骤）。池里只有一个页面时它本就是
        // 前台页，这里再做一次是兜底：reload / 页面重建后激活状态不一定可依赖。
        stage.value = "activate"
        runCatching { page.bringToFront() }.onFailure {
            context.log(LogLevel.WARN, "[PlumBot-Render] 激活页面失败（继续尝试截图）：" + it.message)
        }

        stage.value = "settle"
        val target = targetSettler.settle(page, viewRoute)
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
            .setTimeout(budget.remaining())

        if (format == ScreenshotFormat.JPEG) {
            screenshotOptions.setQuality(
                ScreenshotFormat.quality(context.config.getInteger("render", "playwright", "jpeg_quality"))
            )
        }

        stage.value = "screenshot"
        val bytes = try {
            page.screenshot(screenshotOptions)
        } catch (e: PlaywrightException) {
            // 把失败时的页面现场写进 detail：没有它只能反复猜"为什么截图超时"
            throw failureMapper.failure(
                PlaywrightErrorCodes.SCREENSHOT_FAILED,
                "screenshot",
                viewRoute,
                "截图失败：" + (e.message ?: e::class.java.simpleName) + "；现场 " + targetDiagnostics,
                "若为 TimeoutError：先看现场里的 tab=，hidden 说明页面没被激活到前台（后台标签页不产帧，截图会等到超时）；tab=visible 才需怀疑 READY 之后仍在回流（异步字体/图片），可增大 render_timeout_ms。",
                failureMapper.templateManualCommand(theme),
                e
            )
        }
        if (bytes.isEmpty()) {
            throw failureMapper.failure(
                PlaywrightErrorCodes.SCREENSHOT_FAILED,
                "screenshot",
                viewRoute,
                "截图返回空字节（#render-target 可能不可见或尺寸为 0）；现场 " + targetDiagnostics,
                "确认模板存在 id=render-target 且可见；必要时提高 render_timeout_ms。",
                null
            )
        }
        return bytes
    }

    /** 当前渲染阶段；失败映射靠它拿到准确的 stage（不依赖异常类型猜测）。 */
    private class RenderStage(var value: String = "payload")
}
