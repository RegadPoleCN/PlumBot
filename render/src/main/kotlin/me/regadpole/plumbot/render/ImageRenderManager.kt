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

package me.regadpole.plumbot.render

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.driver.AwtImageRenderer
import me.regadpole.plumbot.render.driver.PlaywrightImageRenderer
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 门户实现（内部类型，外界只能通过 [RenderProvider] 与 [RenderPortal] 使用）。
 *
 * 职责：路由解析（门控 → 逐路由引擎 → 全局引擎 → 路由首选）；全局有界容量（超出立即
 * 降级纯文本，不排队不丢弃）+ 同一目标串行；载荷长度治理；**任何失败都返回
 * [RenderResult]，永不抛异常**。
 */
internal class ImageRenderManager(
    private val context: PlatformContext,
    private val libraryLoader: RenderLibraryLoader? = null,
    private val runtimeWebDir: File = context.dataDirectory.resolve("web").toFile()
) : RenderPortal {

    @Volatile
    private var themeManager = buildThemeManager()
    private val renderers = mutableMapOf<String, ImageRenderer>()
    private val rendererLock = Any()
    private var playwrightRenderer: PlaywrightImageRenderer? = null

    /** 全局渲染容量 = 并发数 + 排队深度；`tryAcquire` 失败即视为队列溢出。 */
    private val capacity = Semaphore(CONCURRENCY + QUEUE_DEPTH)

    /** 同一目标串行渲染，保证出图顺序与消息顺序一致。 */
    private val targetLocks = ConcurrentHashMap<String, Mutex>()

    /** 已提示过的未知引擎取值（去重键是引擎名，不是 route）。 */
    private val engineNotices = ConcurrentHashMap.newKeySet<String>()

    /** 降级提示的去重时间表：键为 "引擎:路由"，值为上次 ERROR 的时间戳。 */
    private val degradationNotices = ConcurrentHashMap<String, Long>()

    /** 已提示过的"全局引擎覆盖路由首选"（键为 "路由:全局取值"）。 */
    private val engineShadowNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过的"路由出图被配置关闭"（键为 route）。 */
    private val gateClosedNotices = ConcurrentHashMap.newKeySet<String>()

    /** 已提示过的"路由引擎为 none"（键为 route）。 */
    private val engineOffNotices = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var downloadUserAgent: String? = null

    @Volatile
    private var avatarCacheRef: AvatarCache? = null

    @Volatile
    private var avatarFetcherRef: AvatarFetcher? = null

    /**
     * 皮肤漏斗解析器。
     *
     * **必须跨调用复用**：它持有的 [FailureCooldown] 是有状态的熔断表，
     * 每次调用新建的话冷却表会随对象一起被回收，熔断形同虚设。
     */
    @Volatile
    private var avatarResolverRef: AvatarResolver? = null

    /** 待注入浏览器的字体 CSS（驱动惰性构造时取用）。 */
    @Volatile
    private var fontCss: String? = null

    /** 最近一次 `configure()` 注入的 TTF；异步重建时只在工作线程读取。 */
    @Volatile
    private var configuredFontFile: File? = null

    /**
     * Playwright 运行库装载门：单飞 + 状态机 + 冷却，**只调度不内联下载**。
     *
     * 状态集中在一处，预热 / 渲染 / doctor 三个入口共用同一份"是否该下载"的判断，
     * 渲染路径因此无法绕开门去触发 190 MB 下载。
     */
    private val playwrightGate = PlaywrightInstallGate(
        loader = libraryLoader,
        scheduleAsync = { task -> context.scheduler.runAsync(task) },
        log = { level, message -> context.log(level, message) },
        cooldownMillis = PLAYWRIGHT_LOAD_COOLDOWN_MS
    )

    /** 字体/主题/引擎表等"配置派生状态"的重建代数；旧任务据此放弃。 */
    private val derivedStateGeneration = AtomicLong(0L)

    /**
     * `configure()` 注入的**目标**配置指纹：决定现有引擎实例是否还能继续服务。
     *
     * 引擎（页面池、浏览器通道、缩放、输出格式、模板主题、字体注入）都在首次使用时按当时
     * 的配置固化，因此只有这组配置变化时才需要重建。
     */
    @Volatile
    private var pendingEngineFingerprint: String = ""

    /** 当前引擎表（可能为空）对应的配置指纹；与 [pendingEngineFingerprint] 不同即需要重建。 */
    @Volatile
    private var appliedEngineFingerprint: String = ""

    /** 已请求、尚未执行的引擎重建（懒重建：在下次真正需要引擎时执行）。 */
    @Volatile
    private var engineResetPending = false

    init {
        // 只装配 AWT。Playwright 驱动**惰性构造**：engine=awt 的用户不能被拖上
        // Playwright 的类校验与其依赖（曾导致启动期 NoClassDefFoundError: com/google/gson/Gson），
        // 也不该凭空多出一套线程池。
        register(AwtImageRenderer(context))
    }

    override val isImageCapable: Boolean
        get() = rendererFor(ENGINE_AWT)?.isSupported == true ||
            runCatching { Class.forName(PLAYWRIGHT_CLASS) }.isSuccess ||
            playwrightGate.isReady()

    /**
     * **只调度** Playwright 按需装载，绝不在调用线程（主线程）上同步下载；真实装载由
     * 平台异步线程执行，本方法立即返回。配置了 playwright 时即使尚未装载完成也返回
     * true，让首帧进入渲染路径（最坏情况由 AWT 兜底），避免启动期把图片直接判死。
     *
     * 必须扫描**所有已启用出图**的路由，而不是只看 [RenderRoute.STATUS]：逐路由覆盖
     * 同样需要预热。
     */
    override fun warmUpEngines(): Boolean {
        val engines = RenderRoute.entries
            .filter { picEnabled(it) }
            .map { runCatching { engineFor(it) }.getOrDefault(ENGINE_AWT) }
            .toSet()
        val playwrightConfigured = ENGINE_PLAYWRIGHT in engines
        if (playwrightConfigured) schedulePlaywrightLoad()
        return isImageCapable || playwrightConfigured
    }

    /**
     * 调度一次异步装载：同一时间只有一个任务；已成功 / 冷却中 / 无 loader 时直接返回。
     * 调度失败只记录 WARN，**绝不**回退到调用线程同步下载。
     */
    private fun schedulePlaywrightLoad() {
        // 门的内部判断：已就绪 / 装载中 / 冷却中 / 无 loader 一律直接返回，且**只调度不阻塞**。
        playwrightGate.startOnce("warm-up")
    }

    /**
     * 真实装载（可能触发 Libby 下载，耗时/阻塞）。**只允许平台异步线程调用**：由
     * [schedulePlaywrightLoad] 调度，或由 `/plumbot render doctor|install` 在异步泳道调用；
     * 禁止在 [warmUpEngines] 或任何主线程路径直接调用 —— 该约束由 [PlaywrightInstallGate]
     * 与三端装载器自身的主线程守卫共同保证。
     *
     * @return null 表示已就绪；否则为**可展示给服主的失败原因**（doctor 直接打印）。
     */
    override fun ensurePlaywrightLibrary(): String? {
        if (playwrightGate.isReady()) return null
        // 平台可能早已把 Playwright 放上类路径（预装 / 别的插件注入），此时不必再下载。
        if (runCatching { Class.forName(PLAYWRIGHT_CLASS) }.isSuccess) {
            playwrightGate.markReady()
            return null
        }
        if (libraryLoader == null) return "未注入 RenderLibraryLoader（平台未注册依赖加载器）"
        return playwrightGate.awaitLoad()
    }

    private fun ensurePlaywrightLoaded(): Boolean = ensurePlaywrightLibrary() == null

    private fun rendererFor(engine: String): ImageRenderer? {
        // 懒重建：配置改过就先把旧引擎摘下交给回收线程，再按新配置构造。
        applyPendingEngineReset()
        if (engine == ENGINE_PLAYWRIGHT) {
            playwrightRenderer?.let { return it }
            return synchronized(rendererLock) {
                playwrightRenderer ?: PlaywrightImageRenderer(context, themeManager, playwrightGate).also {
                    it.updateFontCss(fontCss)
                    playwrightRenderer = it
                    register(it)
                    markEnginesCurrent()
                }
            }
        }

        renderers[engine]?.let { return it }
        if (engine != ENGINE_AWT) return null

        // AWT 引擎按需（重新）装配。
        // `reload()` 会在配置变化时关闭并清空引擎表，若这里只查表不重建，
        // reload 之后**再也出不了图**（渲染一律 Failed）。
        return synchronized(rendererLock) {
            renderers[engine] ?: AwtImageRenderer(context).also {
                register(it)
                markEnginesCurrent()
            }
        }
    }

    override fun configure(config: RenderConfig) {
        configuredFontFile = config.fontFile
        config.fontFile?.let { TextToImg.ttfFile = it }
        config.userAgent?.let { downloadUserAgent = it }
        // 把文本可读性规则注入 AWT 引擎：协议清单与 config.yml 声明的底板色/描边阈值
        // 必须真的被消费，否则三方数值只是"恰好一致"。
        TextToImg.style = toEngineStyle(config.textStyle)
        pendingEngineFingerprint = RenderEngineFingerprint.of(context.config, config.fontFile)
        // 启动期第一次 configure：init() 装配的 AWT 引擎不固化配置，直接认领指纹即可。
        // 否则 applied 一直是空串，启动后第一次 /plumbot reload 会被误判成"配置已变化"
        // 而无谓地重建一次引擎。
        if (appliedEngineFingerprint.isEmpty()) appliedEngineFingerprint = pendingEngineFingerprint
        // 只要有已启用出图的路由配了 playwright，就立刻在异步泳道把运行库装上：命令回复
        // 走 sendRendered 而不经过 warmUpEngines()，否则"只敲命令的服务器"第一次出图必然
        // 报"引擎 playwright 不可用"并降级。
        schedulePlaywrightLoadIfConfigured()
        // 字体 CSS 需要读取最多 16MB 的 TTF，主题解析还会扫描目录并可能触发下载：
        // 两者都不允许在调用线程（通常就是主线程）执行，这里只登记配置并调度重建。
        scheduleDerivedStateRebuild("configure")
    }

    /** 已启用出图的路由里是否出现了 playwright 引擎（只读配置，不做任何 I/O）。 */
    private fun playwrightConfiguredForGateways(): Boolean = RenderRoute.entries.any { route ->
        picEnabled(route) &&
            runCatching { engineFor(route) }.getOrDefault(ENGINE_AWT) == ENGINE_PLAYWRIGHT
    }

    /** 配置要求 playwright 时调度一次异步装载；无 loader / 已成功 / 冷却中均直接返回。 */
    private fun schedulePlaywrightLoadIfConfigured() {
        if (!playwrightConfiguredForGateways()) return
        schedulePlaywrightLoad()
    }

    /**
     * 用**同一个 TTF** 同时供两条引擎使用：AWT 由 [TextToImg.ttfFile] 直接加载，
     * 浏览器由注入 `@font-face` 加载。这样同一条消息无论走哪个引擎字形都一致，
     * 并解决模板侧中文 / Emoji 的豆腐块。
     */
    private fun applyRenderFont(fontFile: File?) {
        val maxBytes = context.config.getInteger("render", "playwright", "font_max_bytes")
            .let { if (it > 0) it else DEFAULT_FONT_MAX_BYTES }

        // 读 TTF / 生成 base64 CSS 在锁外完成（最坏 16MB，绝不能在主线程做）；
        // 只在发布结果时持锁，保证 `rendererFor` 惰性构造时看到的 fontCss 与 updateFontCss 一致。
        val outcome = RenderFontInjector.buildCss(fontFile, maxBytes)
        synchronized(rendererLock) {
            when (outcome) {
                is RenderFontInjector.Outcome.Css -> {
                    fontCss = outcome.text
                    // 驱动可能尚未惰性构造，等它被创建时会取用 fontCss
                    playwrightRenderer?.updateFontCss(outcome.text)
                }

                is RenderFontInjector.Outcome.Skipped -> {
                    fontCss = null
                    playwrightRenderer?.updateFontCss(null)
                    context.log(LogLevel.INFO, "[PlumBot-Render] 未注入页面字体：${outcome.reason}")
                }
            }
        }
    }

    /** 清单/配置 → 引擎样式；非法颜色回落到默认值而不是抛异常。 */
    private fun toEngineStyle(style: RenderTextStyle): TextToImg.Style {
        val fallback = TextToImg.Style.DEFAULT
        val base = AvatarComposer.parseColor(style.baseColor)
        val text = AvatarComposer.parseColor(style.textColor)
        if (base == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 非法底板色 '${style.baseColor}'，已回落默认值")
        }
        if (text == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 非法文字色 '${style.textColor}'，已回落默认值")
        }
        return TextToImg.Style(
            plateColor = base ?: fallback.plateColor,
            textColor = text ?: fallback.textColor,
            autoOutline = style.autoOutline,
            contrastThreshold = style.contrastThreshold,
            maxLineWidth = style.maxLineWidth.takeIf { it >= 0 } ?: fallback.maxLineWidth
        )
    }

    /**
     * 重载：同步摘掉头像管线，字体/主题重建调度到异步线程；**引擎只在指纹变化时才
     * 重建，且推迟到下一次真正需要引擎时**（懒重建）。
     *
     * 引擎与头像管线都是**首次使用时按当时的配置**创建的，配置改动后必须丢弃重建，
     * 否则 reload 只换了主题：引擎选择、页面池参数、缓存目录、头像尺寸仍是旧值。
     * 无条件重建则每次 reload 都要关闭 Chromium（同步等待 node 驱动退出、进行中的
     * 出图降级为文本），还留下一行"底层 Chromium 意外退出"的假故障日志。
     */
    override fun reload() {
        // 先在调用线程摘掉头像管线：丢引用是零 I/O 操作，但它承载着 AvatarResolver 的
        // 熔断表与内存缓存，留给异步任务会让 reload 之后的解析仍命中旧缓存/旧熔断
        // （表现为"改了配置却没生效"），调度失败时重载语义也会失效。
        synchronized(rendererLock) {
            avatarCacheRef = null
            avatarFetcherRef = null
            avatarResolverRef = null
        }
        if (pendingEngineFingerprint == appliedEngineFingerprint) {
            context.log(
                LogLevel.INFO,
                "[PlumBot-Render] 渲染配置未变化，保留现有引擎（跳过 Chromium 关闭与重建）。"
            )
        } else {
            engineResetPending = true
            context.log(
                LogLevel.INFO,
                "[PlumBot-Render] 渲染配置已变化：引擎将在下一次出图前按新配置重建（进行中的出图不受影响）。"
            )
        }
        scheduleDerivedStateRebuild("reload")
    }

    /**
     * 将配置派生状态（字体 CSS、主题管理器）的重建调度到平台异步线程。
     *
     * generation 合并快速连续的 configure/reload，只有最新任务生效；引擎重建**不走
     * 这里**，由 [reload] 置标记、[applyPendingEngineReset] 在需要时执行。
     */
    private fun scheduleDerivedStateRebuild(reason: String) {
        val generation = derivedStateGeneration.incrementAndGet()
        try {
            context.scheduler.runAsync {
                if (generation != derivedStateGeneration.get()) return@runAsync
                try {
                    applyRenderFont(configuredFontFile)
                    if (generation != derivedStateGeneration.get()) return@runAsync
                    refreshThemeManager()
                } catch (t: Throwable) {
                    context.log(
                        LogLevel.WARN,
                        "[PlumBot-Render] 异步重建渲染配置失败（$reason）：${t.message}"
                    )
                }
            }
        } catch (t: Throwable) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 无法调度渲染配置异步重建（$reason）：${t.message}"
            )
        }
    }

    /**
     * 执行被推迟的引擎重建（懒重建），只在**真正需要引擎时**（[rendererFor]）执行。
     *
     * 锁内只摘引用，关闭动作入队到 [RenderTeardown]：关闭 Playwright 会同步等待 node
     * 驱动进程退出，持锁执行会连带阻塞惰性构造与 `shutdown()`。
     */
    private fun applyPendingEngineReset() {
        if (!engineResetPending) return
        val closing: Pair<List<ImageRenderer>, PlaywrightImageRenderer?> = synchronized(rendererLock) {
            if (!engineResetPending) return
            engineResetPending = false
            val snapshot = renderers.values.toList() to playwrightRenderer
            renderers.clear()
            playwrightRenderer = null
            appliedEngineFingerprint = pendingEngineFingerprint
            snapshot
        }
        closeOffThread(closing.first, closing.second)
        // 熔断表随解析器一起重建：配置重载后应给上一次故障的端点一次新机会
        avatarCacheRef = null
        avatarFetcherRef = null
        avatarResolverRef = null
        if (closing.first.isEmpty() && closing.second == null) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 引擎已按新配置重建：后续出图由新引擎完成，旧引擎在后台回收。"
        )
    }

    /** 记录"当前引擎表对应的配置指纹"，供 [reload] 判断是否需要重建。 */
    private fun markEnginesCurrent() {
        appliedEngineFingerprint = pendingEngineFingerprint
    }

    override fun metrics(): Map<String, Long> = RenderMetrics.snapshot()

    override fun renderHealth(): RenderHealth = RenderMetrics.health()

    override fun resetRenderHealth() {
        RenderMetrics.reset()
    }

    /**
     * 关闭门户：**立即返回**，不在调用线程上关闭引擎。
     *
     * 调用方几乎总是服务端主线程。`PlaywrightImageRenderer.shutdown()` 内部的
     * `Browser.close()` 与 `PlaywrightImpl.close()` 会阻塞等待 node 驱动进程退出
     * （profile 实测单 tick 最高 1817ms）：这里只摘引用，关闭动作入队到
     * [RenderTeardown] 的守护线程，失败只 WARN。
     */
    override fun shutdown() {
        // 取消尚未执行的配置重建任务；已进入任务体的那一代也会在锁内看到代数变化。
        derivedStateGeneration.incrementAndGet()
        engineResetPending = false
        val closing: Pair<List<ImageRenderer>, PlaywrightImageRenderer?> = synchronized(rendererLock) {
            val snapshot = renderers.values.toList() to playwrightRenderer
            renderers.clear()
            playwrightRenderer = null
            // 引擎表已空：之后按当前配置惰性重建，不需要再标记"待重建"。
            appliedEngineFingerprint = pendingEngineFingerprint
            snapshot
        }
        targetLocks.clear()
        avatarCacheRef = null
        avatarFetcherRef = null
        avatarResolverRef = null
        closeOffThread(closing.first, closing.second)
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

    /** 把一批引擎的关闭动作交给 [RenderTeardown]（守护线程串行执行）；空列表直接跳过。 */
    private fun closeOffThread(engines: List<ImageRenderer>, playwright: PlaywrightImageRenderer?) {
        if (engines.isEmpty() && playwright == null) return
        RenderTeardown.submit(
            onFailure = { t ->
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] 回收渲染引擎失败：" + (t.message ?: t.javaClass.simpleName)
                )
            }
        ) {
            engines.forEach { runCatching { it.shutdown() } }
            runCatching { playwright?.shutdown() }
        }
    }

    /** 协议字段清单的可读说明（供 doctor 输出）。 */
    override fun describeProtocolFields(): String? = RenderProtocolRules.describe()

    /**
     * 只反映"配置是否启用"：门控关闭或 `engine=none` 视为**主动关闭**；
     * 队列溢出、引擎不可用等属于"启用后失败"，由 [render] 的 Unavailable/Failed 表达。
     */
    override fun isImageEnabled(route: String): Boolean {
        val parsed = RenderRoute.byName(route) ?: return false
        return picEnabled(parsed) && engineFor(parsed) != ENGINE_NONE
    }

    /**
     * 模板能力探测：交给**当前生效驱动**回答（见 [ImageRenderer.supportsRoute]）。
     *
     * 引擎不可用 / 配置为 none → 驱动为 null → `null`（未知），调用方按原顺序尝试；
     * AWT 不看路由 → 默认 `null`；只有 Playwright 会依据 manifest 的 `routes` 给出
     * 明确的 `true`/`false`。
     */
    override fun supportsRoute(route: RenderRoute): Boolean? =
        rendererFor(engineFor(route))?.supportsRoute(route.route)

    override suspend fun render(request: RenderRequest): RenderResult {
        val route = RenderRoute.byName(request.route)
            ?: return RenderResult.Failed("未知路由: ${request.route}")

        if (!picEnabled(route)) {
            noticeGateClosed(route)
            return RenderResult.Unavailable
        }

        val engine = engineFor(route)
        if (engine == ENGINE_NONE) {
            noticeEngineOff(route)
            return RenderResult.Unavailable
        }

        // 协议字段自检：模板与插件各自独立演进，字段漂移只会表现为「图片里某块空白」，
        // 因此在真正出图前做一次键集比对，把漂移变成可诊断的 WARN（同一差异只报一次）。
        RenderProtocolRules.diff(route.route, request.payload)?.let { drift ->
            RenderProtocolRules.report(route.route, drift) { message ->
                context.log(LogLevel.WARN, message)
            }
        }

        val targetKey = request.targetKey
        if (targetKey == null) return renderWithCapacity(route, engine, request)

        val lock = targetLocks.computeIfAbsent(targetKey) { Mutex() }
        lock.lock()
        return try {
            renderWithCapacity(route, engine, request)
        } finally {
            lock.unlock()
            // 水位超限时只清理**空闲**条目：`clear()` 会把其它协程正在持有的 Mutex 一并
            // 丢弃，等待者与新调用者拿到的锁对象不同 ——"同一目标串行渲染"就此失效。
            // `isLocked` 为 false 但仍有人刚排队的极端时序下仍可能被移除，代价是那一瞬间
            // 的串行性，而不是把正在执行的渲染放跑。
            if (targetLocks.size > MAX_TRACKED_TARGETS) {
                targetLocks.entries.removeIf { (key, mutex) -> key != targetKey && !mutex.isLocked }
            }
        }
    }

    override suspend fun fetchAvatarAsDataUrl(cacheKey: String, url: String): String? =
        avatarFetcher().fetchAsDataUrl(cacheKey, url)

    override suspend fun resolveSkinAvatar(playerName: String): String? = withContext(Dispatchers.IO) {
        when (val resolution = avatarResolver().resolve(playerName)) {
            is AvatarResolver.Resolution.Found -> toDataUrl(resolution.png)
            AvatarResolver.Resolution.Miss -> null
        }
    }

    private fun avatarHeadSize(): Int =
        context.config.getInteger("render", "avatar", "head_size")
            .takeIf { it in 16..512 }
            ?: AvatarResolver.DEFAULT_HEAD_SIZE

    override suspend fun composeAvatar(request: AvatarComposeRequest): String? = withContext(Dispatchers.IO) {
        val cache = avatarCache()
        // 缓存键必须带**策略版本 + 尺寸**：合成结果依赖尺寸等配置，
        // 只按调用方给的 key 存盘会让配置变化后继续命中旧图（"改了 head_size 没生效"）。
        // 合成逻辑本身变化时提升 AVATAR_CACHE_POLICY_VERSION 即可作废旧图。
        val key = request.cacheKey
            ?.takeIf { it.isNotBlank() }
            ?.let { "v$AVATAR_CACHE_POLICY_VERSION:h${avatarHeadSize()}:$it" }
        if (key != null) {
            cache.read(key)?.let { return@withContext toDataUrl(it) }
        }
        val composed = runCatching { AvatarComposer.compose(request) }.getOrNull()
            ?: return@withContext null
        if (key != null) cache.write(key, composed)
        toDataUrl(composed)
    }

    /** data URL 前缀按**实际内容**给出（与嗅探保持一致，避免声明 MIME 与内容不符）。 */
    private fun toDataUrl(bytes: ByteArray): String =
        "data:" + (SafeHttp.mimeOf(bytes) ?: "image/png") + ";base64," +
            Base64.getEncoder().encodeToString(bytes)

    private fun avatarFetcher(): AvatarFetcher = avatarFetcherRef ?: synchronized(rendererLock) {
        avatarFetcherRef ?: buildAvatarFetcher().also { avatarFetcherRef = it }
    }

    private fun buildAvatarFetcher(): AvatarFetcher {
        val config = context.config
        return AvatarFetcher(
            context = context,
            cache = avatarCache(),
            allowedHosts = RenderSecurity.allowedHostsFrom(config),
            maxBytes = configuredLimit(config, "per_layer_max", DEFAULT_PER_LAYER_MAX),
            userAgent = downloadUserAgent ?: DEFAULT_RENDER_USER_AGENT
        )
    }

    /**
     * 复用的漏斗解析器（见 [avatarResolverRef] 的说明）。
     *
     * 注意：`head_size` 与冷却时长在构造时读取，因此**改这些配置需要 `reload()`**；
     * `reload()` 同时会重置熔断状态（配置变更后允许重试）。
     */
    private fun avatarResolver(): AvatarResolver = avatarResolverRef ?: synchronized(rendererLock) {
        avatarResolverRef ?: AvatarResolver(context, avatarFetcher(), avatarHeadSize())
            .also { avatarResolverRef = it }
    }

    private fun avatarCache(): AvatarCache = avatarCacheRef ?: synchronized(rendererLock) {
        avatarCacheRef ?: buildAvatarCache().also { avatarCacheRef = it }
    }

    private fun buildAvatarCache(): AvatarCache {
        val config = context.config
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val cacheDir = config.getString("render", "avatar", "cache_dir")
            ?.takeIf { it.isNotBlank() }
            ?.replace("%plugin_folder%", pluginFolder)
            ?.let { File(it) }
            ?: File(runtimeWebDir.parentFile ?: runtimeWebDir, "cache/avatars")
        val ttlHours = config.getInteger("render", "avatar", "cache_ttl_hours")
            .let { if (it <= 0) DEFAULT_AVATAR_TTL_HOURS else it }
        return AvatarCache(cacheDir, ttlHours * 60L * 60L * 1000L)
    }

    private suspend fun renderWithCapacity(
        route: RenderRoute,
        engine: String,
        request: RenderRequest
    ): RenderResult {
        if (!capacity.tryAcquire()) {
            // 队列溢出会静默降级为纯文本：计入渲染指标（`:render` 不依赖 `:common`，指标自持）
            RenderMetrics.increment(RenderMetrics.QUEUE_REJECTED)
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 渲染容量已满（并发 $CONCURRENCY + 排队 $QUEUE_DEPTH），路由 ${route.route} 立即降级为纯文本。"
            )
            return RenderResult.Unavailable
        }
        val result = try {
            doRender(route, engine, request)
        } finally {
            capacity.release()
        }
        // 门户是渲染结果的唯一计数者（见 RenderHealth）：成功出图与"最终无图"在这里统一归类。
        // 注意 Unavailable 有两种来源：容量拒绝（前面已 return，不计入此处）与前置条件不足
        // （载荷超限 / 兜底引擎缺失），后者由 doRender 内部按 failed 记录并带原因。
        when (result) {
            is RenderResult.Image -> RenderMetrics.increment(RenderMetrics.EMITTED)
            is RenderResult.Failed -> {
                RenderMetrics.increment(RenderMetrics.DEGRADED_FAILED)
                RenderMetrics.noteFailure(route.route, result.reason)
            }

            RenderResult.Unavailable -> Unit
        }
        return result
    }

    /**
     * 记一次"被前置条件挡住、未真正尝试渲染"（载荷超限、兜底引擎缺失）。
     *
     * 口径见 [RenderHealth]：这类失败计入 failed 并留下原因，但不计入 degraded
     * （主引擎根本没被调用）。
     */
    private fun unavailable(route: RenderRoute, reason: String): RenderResult {
        RenderMetrics.increment(RenderMetrics.DEGRADED_FAILED)
        RenderMetrics.noteFailure(route.route, reason)
        return RenderResult.Unavailable
    }

    private suspend fun doRender(
        route: RenderRoute,
        engine: String,
        request: RenderRequest
    ): RenderResult {
        val options = RenderOptions(timeoutMs = request.timeoutMs ?: timeoutFor(route))
        // null = 卡片正文超过 card_text_max：截断会让图片与文字分叉，宁可不出图。
        val payload = governPayload(request.payload)
            ?: return unavailable(route, "卡片正文超过 card_text_max，已放弃出图")

        // 配了 playwright 但运行库还没装上时在这里补一次异步装载。
        // 没有这一步，命令回复等"从不调用 warmUpEngines()"的路径永远拿不到 Playwright。
        if (engine == ENGINE_PLAYWRIGHT && !playwrightGate.isReady() && !isPlaywrightPresent()) {
            schedulePlaywrightLoad()
        }

        val primary = rendererFor(engine)?.takeIf { it.isSupported }
        var primaryFailure: String? = null

        if (primary != null) {
            val bytes = try {
                primary.render(route.route, payload, options)
            } catch (t: Throwable) {
                primaryFailure = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                null
            }
            if (bytes != null && isRenderableImage(bytes)) return RenderResult.Image(bytes)
            if (primaryFailure == null) {
                primaryFailure = if (bytes == null) "引擎未返回图片数据" else "引擎返回的数据不是有效 PNG"
            }
        } else if (engine != ENGINE_AWT) {
            RenderMetrics.increment(RenderMetrics.ENGINE_UNAVAILABLE)
            primaryFailure = when {
                engine != ENGINE_PLAYWRIGHT -> "引擎 $engine 不可用（未装载或初始化失败）"
                !isPlaywrightPresent() -> "Playwright 运行库尚未装载完成（正在后台装载）"
                else -> "Playwright 引擎未能初始化"
            }
        }

        // 主引擎即 AWT 时不再重复渲染一次
        if (engine == ENGINE_AWT) {
            return RenderResult.Failed(
                "AWT 引擎未能产出有效图片（路由 ${route.route}）：${primaryFailure ?: "未知原因"}"
            )
        }

        // fallback_to_awt=false：主引擎失败即降级纯文本，不允许悄悄换成 AWT 图。
        if (!fallbackToAwt()) {
            reportDegradation(route, engine, primaryFailure ?: "未知原因", fallbackSucceeded = false)
            return RenderResult.Failed(
                "路由 ${route.route} 的 $engine 渲染失败：${primaryFailure ?: "未知原因"}；" +
                    "render.playwright.fallback_to_awt=false，本次降级为纯文本。"
            )
        }

        // 兜底同样要走"按需重建"的查找（reload 后引擎表被清空，直接查表会拿不到）
        val fallback = rendererFor(ENGINE_AWT)?.takeIf { it.isSupported }
        if (fallback == null) {
            reportDegradation(route, engine, primaryFailure ?: "未知原因", fallbackSucceeded = false)
            return unavailable(route, "AWT 兜底引擎不可用，本次没有出图")
        }
        val fallbackBytes = try {
            fallback.render(route.route, payload, options)
        } catch (t: Throwable) {
            context.log(LogLevel.WARN, "[PlumBot-Render] AWT 兜底失败: ${t.message}")
            null
        }
        return if (fallbackBytes != null && isRenderableImage(fallbackBytes)) {
            reportDegradation(route, engine, primaryFailure ?: "未知原因", fallbackSucceeded = true)
            RenderResult.Image(fallbackBytes)
        } else {
            reportDegradation(route, engine, primaryFailure ?: "未知原因", fallbackSucceeded = false)
            RenderResult.Failed("AWT 兜底未能产出有效图片（路由 ${route.route}）")
        }
    }

    /**
     * 是否允许主引擎失败后改用 AWT 兜底。
     *
     * 缺省必须是 true（兼容旧配置与测试替身）；显式 false 时宁可降级纯文本，
     * 也不让服主以为 Playwright 正在工作、实际看到的却是 AWT 图。
     */
    private fun fallbackToAwt(): Boolean {
        val raw = runCatching {
            context.config.getString("render", "playwright", "fallback_to_awt")
        }.getOrNull() ?: return true
        return raw.toBooleanStrictOrNull() ?: true
    }

    /**
     * 记录一次"主引擎失败 → AWT 降级"。
     *
     * 为什么是 ERROR 而不是 WARN：降级意味着服主看到的图与所配引擎不符，
     * 属于必须可发现的配置/依赖问题。同一引擎+路由在 [DEGRADATION_LOG_INTERVAL_MS]
     * 内只打一次，避免每条消息刷屏；计数仍逐次累加。
     */
    private fun reportDegradation(
        route: RenderRoute,
        engine: String,
        reason: String,
        fallbackSucceeded: Boolean
    ) {
        RenderMetrics.increment(RenderMetrics.FALLBACK)
        if (fallbackSucceeded) RenderMetrics.increment(RenderMetrics.FALLBACK_AWT)
        RenderMetrics.noteFailure(route.route, "$engine 渲染失败：$reason")
        val key = "$engine:${route.route}"
        val now = System.currentTimeMillis()
        val last = degradationNotices[key] ?: 0L
        if (now - last < DEGRADATION_LOG_INTERVAL_MS) return
        degradationNotices[key] = now
        val outcome = if (fallbackSucceeded) "已用 AWT 兜底出图" else "AWT 兜底也未成功，本次降级为纯文本"
        // 装载进行中/冷却中不是"配置或依赖出错"，用 INFO 说明现状即可；
        // 真正的故障（引擎在但初始化失败）仍保持 ERROR 以便被发现。
        val loading = playwrightGate.isLoading()
        val cooling = playwrightGate.isCooling()
        val level = if (loading || cooling) LogLevel.INFO else LogLevel.ERROR
        val hint = when {
            loading -> "Playwright 运行库正在后台装载，装载完成后自动改用高清引擎（无需再次操作）。"
            cooling -> "Playwright 运行库上次装载失败，正处于冷却中；执行 /plumbot doctor render 查看原因。"
            else -> "执行 /plumbot doctor render 查看依赖与环境诊断。"
        }
        context.log(
            level,
            "[PlumBot-Render] 路由 ${route.route} 的 $engine 渲染失败：$reason；$outcome。$hint"
        )
    }

    /** Playwright 类是否已在类路径上（Libby 按需装载成功的判据）。 */
    private fun isPlaywrightPresent(): Boolean =
        runCatching { Class.forName(PLAYWRIGHT_CLASS) }.isSuccess

    /**
     * 引擎解析：`render.views.<route>.engine` → `render.engine` → 路由首选 → 内置默认 `awt`。
     *
     * **显式配置优先，路由首选只是缺省值**：内置模板的 `render.engine` 恒存在，若按
     * `specific ?: global ?: fallback` 解析，路由首选（如头像卡的 playwright）永远走不到。
     * 因此 `auto`（或缺失 / 空串）视为"未显式配置"，此时才用 [RenderRoute.preferredEngine]；
     * 用户显式写 awt/playwright/none 时仍以其为准。未知取值**一次性 WARN** 后落到 AWT，
     * 见 [warnIfUnknownEngine]；门控见 [picEnabled]。
     */
    private fun engineFor(route: RenderRoute): String {
        val specific = context.config
            .getString("render", "views", route.route, "engine")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val global = context.config
            .getString("render", "engine")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        // auto = "没选"，不是引擎名：逐路由 auto 表示"忽略全局、跟随该路由首选"。
        val specificExplicit = specific?.takeUnless { it.equals(ENGINE_AUTO, ignoreCase = true) }
        val globalExplicit = global?.takeUnless { it.equals(ENGINE_AUTO, ignoreCase = true) }
        val explicit = specificExplicit ?: if (specific != null) null else globalExplicit
        val engine = (explicit ?: route.preferredEngine ?: ENGINE_AWT).lowercase()
        warnIfUnknownEngine(route, engine)
        noticeGlobalEngineOverride(route, specific, global, engine)
        return engine
    }

    /**
     * 全局引擎覆盖了路由首选引擎时，**一次性 INFO** 说清原因。
     *
     * 模板里 `engine: "awt"` 是显式值，升级后不会自动变成 auto，头像卡会一直是无头像
     * 的 AWT 图；没有这条提示就只能靠读文档猜。只在"全局显式"且与首选不一致时提示，
     * 逐路由覆盖视为用户有意为之。
     */
    private fun noticeGlobalEngineOverride(route: RenderRoute, specific: String?, global: String?, engine: String) {
        val preferred = route.preferredEngine ?: return
        // 逐路由显式配置（含显式 auto）都视为用户有意为之，不再提示。
        if (specific != null) return
        if (global == null || global.equals(ENGINE_AUTO, ignoreCase = true)) return
        if (engine.equals(preferred, ignoreCase = true)) return
        if (!engineShadowNotices.add("${route.route}:$global")) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] render.engine=$global 覆盖了路由 ${route.route} 的首选引擎 $preferred —— " +
                "把 render.engine 设为 auto 可按路由自动选择，或用 render.views.${route.route}.engine 单独指定"
        )
    }

    /**
     * 未知引擎值 → **一次性 WARN** 并说明已回落 AWT。
     *
     * 没有取值白名单时，任何拼写错误（`playwrite` / `palywright`）都会**静默**落到 AWT：
     * 服主以为开了高清渲染，实际一直是本地轻量图，且没有任何线索可查。
     * 去重键是**引擎取值**而非 route，同一个错拼只在首次出现时提示一次。
     */
    private fun warnIfUnknownEngine(route: RenderRoute, engine: String) {
        if (engine in KNOWN_ENGINES) return
        if (!engineNotices.add(engine)) return
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 未知渲染引擎 '$engine'（首次出现在路由 ${route.route}）—— " +
                "已回落 $ENGINE_AWT。可选值：$ENGINE_NONE / $ENGINE_AWT / $ENGINE_PLAYWRIGHT / $ENGINE_AUTO"
        )
    }

    /**
     * 出图门控：`render.outputs.<功能>`，图片开关的唯一真源。
     *
     * 每个路由都有门控（`TEXT` 对应 `render.outputs.text`），`featureGate` 为 null 的只有
     * 测试 / 未来扩展；`AVATAR` 与 `HYTALE_AVATAR` 共用 `render.outputs.avatar`。
     * 键由内置模板保证存在，`ConfigMigrator` 也会为旧配置补齐。
     */
    private fun picEnabled(route: RenderRoute): Boolean {
        val gate = route.featureGate ?: return true
        return context.config.getBoolean("render", "outputs", gate)
    }

    /**
     * 门控关闭时**一次性 INFO**：这是"本该出图却只发文字"最主要的静默原因。
     * [render] 直接返回 Unavailable 且 [renderWithCapacity] 不为 Unavailable 计数，
     * 若无提示，`/plumbot render stats` 的"配置关闭"恒为 0，服主没有任何线索。
     */
    private fun noticeGateClosed(route: RenderRoute) {
        if (!gateClosedNotices.add(route.route)) return
        val gate = route.featureGate
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 路由 ${route.route} 的出图已关闭" +
                (if (gate != null) "（render.outputs.$gate=false）" else "") +
                " —— 该功能将始终发送纯文本"
        )
    }

    /** `engine=none` 是主动关闭出图，与门控关闭分开提示，避免排查时混为一谈。 */
    private fun noticeEngineOff(route: RenderRoute) {
        if (!engineOffNotices.add(route.route)) return
        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 路由 ${route.route} 的引擎为 $ENGINE_NONE（render.engine 或 " +
                "render.views.${route.route}.engine）—— 该功能将始终发送纯文本"
        )
    }

    private fun timeoutFor(route: RenderRoute): Long {
        val specific = context.config.getInteger("render", "views", route.route, "timeout_ms")
        if (specific > 0) return specific.toLong()
        val global = context.config.getInteger("render", "playwright", "render_timeout_ms")
        return if (global > 0) global.toLong() else DEFAULT_TIMEOUT_MS
    }

    private fun refreshThemeManager() {
        // 构建会读配置并扫 dev/运行时目录，放在锁外；发布时持锁，避免覆盖并发构造的新驱动。
        val rebuilt = buildThemeManager()
        synchronized(rendererLock) {
            themeManager = rebuilt
            playwrightRenderer?.updateThemeManager(rebuilt)
        }
        // 这三个都是"配置派生 + 有状态"，必须一起失效并在下次使用时重建：
        // 缓存目录/有效期、出口白名单/体积上限/UA、以及漏斗的熔断表。
        avatarCacheRef = null
        avatarFetcherRef = null
        avatarResolverRef = null
    }

    private fun buildThemeManager(): ThemeManager {
        val devDir = context.config.getString("render", "playwright", "dev_web_dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
        return ThemeManager(context, runtimeWebDir, devDir, downloadUserAgent ?: DEFAULT_RENDER_USER_AGENT)
    }

    private fun register(renderer: ImageRenderer) {
        renderers[renderer.type.lowercase()] = renderer
    }

    /** 仅供测试：注册可控引擎，确定性验证**队列容量**与**同目标串行**（真实引擎太快，进不了满载态）。 */
    internal fun registerEngineForTest(renderer: ImageRenderer) {
        register(renderer)
    }

    /**
     * 三层长度治理，取代旧实现的「500 字符静默截断」。
     *
     * `data:` URL（服务端预取并合成的头像图层）**不参与文本截断**，否则会被截成坏图。
     *
     * @return 治理后的载荷；卡片正文超过 `render.limits.card_text_max` 时返回 `null`，
     *   表示"放弃出图、直接发纯文本"（调用方仍有完整正文，用户不会看到半截内容）。
     */
    private fun governPayload(payload: Any): Any? {
        val config = context.config
        val governor = PayloadGovernor(
            displayTextMax = configuredLimit(config, "display_text_max", DEFAULT_DISPLAY_TEXT_MAX),
            perLayerMaxBytes = configuredLimit(config, "per_layer_max", DEFAULT_PER_LAYER_MAX),
            binaryTotalMaxBytes = configuredLimit(config, "binary_total_max", DEFAULT_BINARY_TOTAL_MAX),
            layersMax = configuredLimit(config, "layers_max", DEFAULT_LAYERS_MAX),
            textTotalWarn = configuredLimit(config, "total_text_warn", DEFAULT_TOTAL_TEXT_WARN),
            cardTextMax = configuredLimit(config, "card_text_max", DEFAULT_CARD_TEXT_MAX),
            warn = { message -> context.log(LogLevel.WARN, message) }
        )
        val governed = governor.govern(payload)
        governor.report()
        if (governor.cardTextOverflow) return null
        return governed ?: payload
    }

    private fun configuredLimit(config: PlatformConfig, key: String, fallback: Int): Int {
        val value = config.getInteger("render", "limits", key)
        return if (value <= 0) fallback else value
    }

    /**
     * 校验产出确实是**图片**（主引擎与 AWT 兜底同一套校验）。
     *
     * 必须接受 JPEG：卡片图可配置为 JPEG 档，只认 PNG 会把 JPEG 卡片误判为失败并触发
     * 无谓的兜底重渲染。
     */
    private fun isRenderableImage(bytes: ByteArray): Boolean {
        val mime = SafeHttp.mimeOf(bytes)
        return mime == "image/png" || mime == "image/jpeg"
    }

    private companion object {
        const val ENGINE_NONE = "none"
        const val ENGINE_AWT = "awt"
        const val ENGINE_PLAYWRIGHT = "playwright"

        /** `auto`：未显式选择引擎，交给 [RenderRoute.preferredEngine]（缺省 awt）。 */
        const val ENGINE_AUTO = "auto"

        /** 合法引擎取值；不在其中的一律 WARN 并回落 AWT。 */
        val KNOWN_ENGINES = setOf(ENGINE_AUTO, ENGINE_NONE, ENGINE_AWT, ENGINE_PLAYWRIGHT)

        const val PLAYWRIGHT_CLASS = "com.microsoft.playwright.Playwright"

        /**
         * 并发渲染数。
         *
         * Playwright 的实际并发是 1：页面池只放 1 个页面（前台页），后台标签页不产合成帧，
         * `page.screenshot` 只会等到超时，多页并发还会互抢前台。保留 2 是为了 AWT 兜底
         * 仍可并行，同时让多出来的请求在信号量上排队而不是直接降级。
         */
        const val CONCURRENCY = 2

        /** 允许排队的深度；超出立即降级纯文本，**不排队、不丢弃**。 */
        const val QUEUE_DEPTH = 6

        /** Playwright 装载失败的冷却时长：避免离线环境中每条消息都触发下载尝试。 */
        const val PLAYWRIGHT_LOAD_COOLDOWN_MS = 60_000L

        /** 同一引擎+路由的降级 ERROR 去重窗口。 */
        const val DEGRADATION_LOG_INTERVAL_MS = 5 * 60 * 1000L

        /** 卡片正文上限（字符）：超限不出图，避免巨量文本塞进无头浏览器后超时降级。 */
        const val DEFAULT_CARD_TEXT_MAX = 8000


        const val MAX_TRACKED_TARGETS = 512

        const val DEFAULT_TIMEOUT_MS = 3500L

        /** 展示文本单字段上限；后续由协议清单按 route 细分覆盖。 */
        const val DEFAULT_DISPLAY_TEXT_MAX = 1024

        /** 单个图层（data URL）字节上限。 */
        const val DEFAULT_PER_LAYER_MAX = 256 * 1024

        /** 二进制总量硬上限。 */
        const val DEFAULT_BINARY_TOTAL_MAX = 1024 * 1024

        /** 图层数上限（头部相关部件约 10 层，留足余量）。 */
        const val DEFAULT_LAYERS_MAX = 16

        /** 文本字符总量告警阈值。 */
        const val DEFAULT_TOTAL_TEXT_WARN = 256 * 1024

        /** 头像磁盘缓存默认有效期（小时）。 */
        const val DEFAULT_AVATAR_TTL_HOURS = 168

        /** 头像合成缓存键的策略版本：合成逻辑或键结构变化时 +1，使旧图自动失效。 */
        const val AVATAR_CACHE_POLICY_VERSION = 1

        /** 注入页面的字体体积上限（默认 16MB）：防止把超大字体塞进每一页。 */
        const val DEFAULT_FONT_MAX_BYTES = 16 * 1024 * 1024

        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
    }
}
