// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline

import java.io.File
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.RenderConfig
import me.regadpole.plumbot.render.RenderHealth
import me.regadpole.plumbot.render.RenderLibraryLoader
import me.regadpole.plumbot.render.RenderPortal
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.engine.AwtImageRenderer
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.TextToImg
import me.regadpole.plumbot.render.install.RenderEngineFingerprint
import me.regadpole.plumbot.render.pipeline.assets.AvatarService
import me.regadpole.plumbot.render.pipeline.assets.ThemeService
import me.regadpole.plumbot.render.pipeline.engines.EngineRegistry
import me.regadpole.plumbot.render.pipeline.engines.PlaywrightLifecycle
import me.regadpole.plumbot.render.pipeline.flow.CapacityGate
import me.regadpole.plumbot.render.pipeline.flow.RenderPipeline
import me.regadpole.plumbot.render.pipeline.routing.NoticeSink
import me.regadpole.plumbot.render.pipeline.routing.RoutePolicy
import me.regadpole.plumbot.render.support.RenderProtocolRules

/**
 * 门户实现（内部类型，外界只能通过 [RenderProvider] 与 [RenderPortal] 使用）。
 *
 * 这是**装配点与委托层**，不再承载业务逻辑：门控与引擎解析交给
 * [RoutePolicy]，提示去重交给 [NoticeSink]，容量与同目标串行交给 [CapacityGate]，
 * 引擎表与懒重建交给 [EngineRegistry]，运行库装载交给 [PlaywrightLifecycle]，
 * 头像与主题交给 [AvatarService] / [ThemeService]，渲染主流程交给 [RenderPipeline]。
 *
 * 对外契约不变：**任何失败都返回 [RenderResult]，永不抛异常**。
 */
internal class ImageRenderManager(
    private val context: PlatformContext,
    private val libraryLoader: RenderLibraryLoader? = null,
    private val runtimeWebDir: File = context.dataDirectory.resolve("web").toFile()
) : RenderPortal {

    /** 保护引擎表 / 主题 / 头像引用的**唯一**锁；协作者共用（构造参数传入）。 */
    private val rendererLock = Any()

    /** `configure()` 注入的下载 UA；主题/头像/字体协作者在构造时取用，故放在最前。 */
    @Volatile
    private var downloadUserAgent: String? = null

    private val notices: NoticeSink = NoticeSink(context)

    private val policy: RoutePolicy = RoutePolicy(context, notices)

    private val lifecycle: PlaywrightLifecycle = PlaywrightLifecycle(context, libraryLoader, policy)

    private val themeService: ThemeService = ThemeService(
        context = context,
        runtimeWebDir = runtimeWebDir,
        lock = rendererLock,
        userAgentOf = { downloadUserAgent },
        publishTheme = { theme -> registry.forwardTheme(theme) },
        publishFontCss = { css -> registry.forwardFontCss(css) },
        invalidateAvatars = { avatars.invalidate() },
        onConfigReloaded = {
            registry.noteConfigReload()
            registry.scheduleDerivedStateRebuild("reload")
        }
    )

    private val avatars: AvatarService = AvatarService(
        context = context,
        runtimeWebDir = runtimeWebDir,
        lock = rendererLock,
        userAgentOf = { downloadUserAgent }
    )

    private val registry: EngineRegistry = EngineRegistry(
        context = context,
        lock = rendererLock,
        lifecycle = lifecycle,
        policy = policy,
        themeOf = { themeService.themeManager },
        fontCssOf = { themeService.fontCss },
        applyFont = { themeService.applyRenderFont(themeService.configuredFontFile()) },
        refreshTheme = { themeService.refreshThemeManager() },
        invalidateAvatars = { avatars.invalidate() }
    )

    private val capacity: CapacityGate = CapacityGate(context)

    private val pipeline: RenderPipeline =
        RenderPipeline(context, policy, registry, lifecycle, notices, capacity)

    init {
        // 只装配 AWT。Playwright 驱动**惰性构造**：engine=awt 的用户不能被拖上
        // Playwright 的类校验与其依赖（曾导致启动期 NoClassDefFoundError: com/google/gson/Gson），
        // 也不该凭空多出一套线程池。
        registry.register(AwtImageRenderer(context))
    }

    override val isImageCapable: Boolean
        get() = registry.isImageCapable

    override fun warmUpEngines(): Boolean = registry.warmUpEngines()

    /** 真实装载只在平台异步泳道执行，见 [PlaywrightLifecycle.ensurePlaywrightLibrary]。 */
    override fun ensurePlaywrightLibrary(): String? = lifecycle.ensurePlaywrightLibrary()

    override fun configure(config: RenderConfig) {
        themeService.setConfiguredFont(config.fontFile)
        config.fontFile?.let { TextToImg.ttfFile = it }
        config.userAgent?.let { downloadUserAgent = it }
        // 把文本可读性规则注入 AWT 引擎：协议清单与 config.yml 声明的底板色/描边阈值
        // 必须真的被消费，否则三方数值只是"恰好一致"。
        TextToImg.style = themeService.toEngineStyle(config.textStyle)
        registry.updateConfiguredFingerprint(RenderEngineFingerprint.of(context.config, config.fontFile))
        // 只要有已启用出图的路由配了 playwright，就立刻在异步泳道把运行库装上：命令回复
        // 走 sendRendered 而不经过 warmUpEngines()，否则"只敲命令的服务器"第一次出图必然
        // 报"引擎 playwright 不可用"并降级。
        lifecycle.schedulePlaywrightLoadIfConfigured()
        // 字体 CSS 需要读取最多 16MB 的 TTF，主题解析还会扫描目录并可能触发下载：
        // 两者都不允许在调用线程（通常就是主线程）执行，这里只登记配置并调度重建。
        registry.scheduleDerivedStateRebuild("configure")
    }

    /** 重载：同步摘掉头像管线，字体/主题与引擎按指纹重建，详见 [ThemeService.reload]。 */
    override fun reload() = themeService.reload()

    override fun metrics(): Map<String, Long> = RenderMetrics.snapshot()

    override fun renderHealth(): RenderHealth = RenderMetrics.health()

    override fun resetRenderHealth() {
        RenderMetrics.reset()
    }

    /** 关闭门户：**立即返回**，引擎回收由 [PlaywrightLifecycle.closeOffThread] 入队。 */
    override fun shutdown() {
        registry.shutdown()
        capacity.clear()
    }

    /**
     * 关闭并**等待**回收完成（停服路径专用，见 [RenderPortal.shutdownAndAwait]）。
     *
     * 必须等 [RenderTeardown] 排空：插件 `disable()` 返回后平台会关闭类加载器，若回收
     * 线程仍在执行 `PlaywrightImpl.close()`，线上实测抛
     * "The plugin classloader for PlumBot has thrown a zip file error."。
     */
    override fun shutdownAndAwait(timeoutMs: Long): Boolean {
        shutdown()
        if (timeoutMs <= 0L) return true
        val drained = RenderTeardown.awaitIdle(timeoutMs)
        if (!drained) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 引擎拆除未在 " + timeoutMs + "ms 内完成；Chromium 驱动进程会随 JVM 退出被回收。"
            )
        }
        return drained
    }

    /** 协议字段清单的可读说明（供 doctor 输出）。 */
    override fun describeProtocolFields(): String? = RenderProtocolRules.describe()

    override fun isImageEnabled(route: String): Boolean = policy.isImageEnabled(route)

    override fun supportsRoute(route: RenderRoute): Boolean? =
        policy.supportsRoute(route) { engine -> registry.rendererFor(engine) }

    override suspend fun render(request: RenderRequest): RenderResult = pipeline.render(request)

    override suspend fun fetchAvatarAsDataUrl(cacheKey: String, url: String): String? =
        avatars.fetchAsDataUrl(cacheKey, url)

    override suspend fun resolveSkinAvatar(playerName: String): String? =
        avatars.resolveSkinAvatar(playerName)

    override suspend fun composeAvatar(request: AvatarComposeRequest): String? =
        avatars.composeAvatar(request)

    /** 仅供测试：注册可控引擎，确定性验证**队列容量**与**同目标串行**（真实引擎太快，进不了满载态）。 */
    internal fun registerEngineForTest(renderer: ImageRenderer) {
        registry.register(renderer)
    }
}
