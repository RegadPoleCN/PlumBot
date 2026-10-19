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

/**
 * 门户实现（内部类型，外界只能通过 [RenderProvider] 与 [RenderPortal] 使用）。
 *
 * 职责：
 * 1. **路由解析**：route → 门控（`render.outputs.<功能>`）→ 引擎（`render.views.<route>.engine` → `render.engine` → 内置默认）；
 * 2. **并发与顺序**：全局有界容量（超出立即降级纯文本，不排队不丢弃）+ 同一目标串行；
 * 3. **载荷治理**：三层长度治理（§5）；
 * 4. **降级契约**：任何失败都返回 [RenderResult]，**永不抛异常**。
 */
internal class ImageRenderManager(
    private val context: PlatformContext,
    private val libraryLoader: RenderLibraryLoader? = null,
    private val runtimeWebDir: File = context.dataDirectory.resolve("web").toFile()
) : RenderPortal {

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

    init {
        // 只装配 AWT。Playwright 驱动**惰性构造** —— 旧实现在门户构造时就 new 出驱动，
        // 于是 engine=awt 的用户也会被拖上 Playwright 的类校验与其依赖（曾导致
        // 启动期 NoClassDefFoundError: com/google/gson/Gson），还会凭空多出一套线程池。
        register(AwtImageRenderer(context))
    }

    override val isImageCapable: Boolean
        get() = rendererFor(ENGINE_AWT)?.isSupported == true ||
            runCatching { Class.forName(PLAYWRIGHT_CLASS) }.isSuccess ||
            playwrightLoadSucceeded

    /**
     * R17：**先触发按需装载，再判定**能否出图。
     *
     * 为什么不能只看 [isImageCapable]：Playwright 的类由 Libby 在**首次需要时**下载装载，
     * 而按需装载此前只发生在"驱动构造"里。于是调用方的门控会在装载被触发**之前**判定
     * "不可出图"，那条装载路径永远走不到 —— 表现为"配了 `engine=playwright` 却一直出纯文本"。
     *
     * 必须扫描**所有已启用出图**的路由，而不是只看 [RenderRoute.STATUS]：
     * 全局 `awt` + `render.views.status.engine: playwright` 时，STATUS 之外的覆盖同样需要预热。
     */
    override fun warmUpEngines(): Boolean {
        val engines = RenderRoute.entries
            .filter { picEnabled(it) }
            .map { runCatching { engineFor(it) }.getOrDefault(ENGINE_AWT) }
            .toSet()
        if (ENGINE_PLAYWRIGHT in engines) {
            // 装载失败仍可由 AWT 兜底；这里只负责把"首次需要"的装载路径提前触发。
            ensurePlaywrightLoaded()
        }
        return isImageCapable
    }

    /** 已成功装载过（避免重复触发下载）。 */
    @Volatile
    private var playwrightLoadSucceeded = false

    /** 装载失败冷却（R17）：离线/仓库不可达时不要每条消息都重试一次下载。 */
    private val playwrightLoadCooldown = FailureCooldown(ttlMillis = PLAYWRIGHT_LOAD_COOLDOWN_MS)

    private fun ensurePlaywrightLoaded(): Boolean {
        if (playwrightLoadSucceeded) return true
        if (runCatching { Class.forName(PLAYWRIGHT_CLASS) }.isSuccess) {
            playwrightLoadSucceeded = true
            return true
        }

        val loader = libraryLoader ?: return false
        if (playwrightLoadCooldown.isCooling(PLAYWRIGHT_LOAD_KEY)) return false

        return try {
            val loaded = loader.ensurePlaywright()
            if (loaded) {
                playwrightLoadSucceeded = true
                playwrightLoadCooldown.clear(PLAYWRIGHT_LOAD_KEY)
                true
            } else {
                playwrightLoadCooldown.recordFailure(PLAYWRIGHT_LOAD_KEY)
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] Playwright 按需装载失败（离线或仓库不可达）：" +
                        "已进入 ${PLAYWRIGHT_LOAD_COOLDOWN_MS / 1000}s 冷却，本引擎暂时不可用。"
                )
                false
            }
        } catch (t: Throwable) {
            playwrightLoadCooldown.recordFailure(PLAYWRIGHT_LOAD_KEY)
            context.log(LogLevel.WARN, "[PlumBot-Render] 按需装载异常：${t.message}")
            false
        }
    }

    private fun rendererFor(engine: String): ImageRenderer? {
        if (engine == ENGINE_PLAYWRIGHT) {
            playwrightRenderer?.let { return it }
            return synchronized(rendererLock) {
                playwrightRenderer ?: PlaywrightImageRenderer(context, themeManager, libraryLoader).also {
                    it.updateFontCss(fontCss)
                    playwrightRenderer = it
                    register(it)
                }
            }
        }

        renderers[engine]?.let { return it }
        if (engine != ENGINE_AWT) return null

        // AWT 引擎按需（重新）装配。
        // `reload()` 会关闭并清空引擎表（让它们按新配置重建），若这里只查表不重建，
        // reload 之后**再也出不了图**（渲染一律 Failed）—— 这正是 A14 的回归点。
        return synchronized(rendererLock) {
            renderers[engine] ?: AwtImageRenderer(context).also { register(it) }
        }
    }

    override fun configure(config: RenderConfig) {
        config.fontFile?.let { TextToImg.ttfFile = it }
        config.userAgent?.let { downloadUserAgent = it }
        // 把文本可读性规则注入 AWT 引擎。此前 Style 是写死的，协议清单与 config.yml
        // 里声明的底板色/描边阈值**没有任何消费者**，三方数值只是"恰好一致"。
        TextToImg.style = toEngineStyle(config.textStyle)
        applyRenderFont(config.fontFile)
        refreshThemeManager()
    }

    /**
     * 用**同一个 TTF** 同时供两条引擎使用：AWT 由 [TextToImg.ttfFile] 直接加载，
     * 浏览器由注入 `@font-face` 加载。这样同一条消息无论走哪个引擎字形都一致，
     * 并解决模板侧中文 / Emoji 的豆腐块（基线 E4）。
     */
    private fun applyRenderFont(fontFile: File?) {
        val maxBytes = context.config.getInteger("render", "playwright", "font_max_bytes")
            .let { if (it > 0) it else DEFAULT_FONT_MAX_BYTES }

        when (val outcome = RenderFontInjector.buildCss(fontFile, maxBytes)) {
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
            contrastThreshold = style.contrastThreshold
        )
    }

    override fun reload() {
        refreshThemeManager()

        // 引擎与头像管线都是**首次使用时按当时的配置**创建的；配置改动后必须丢弃重建，
        // 否则 reload 只换了主题：引擎选择、页面池参数、缓存目录、头像尺寸仍是旧值，
        // 而且不会给出任何提示（表现为"改了配置没生效"）。
        //
        // 代价说明：这里会关闭现有引擎（Playwright 浏览器进程随之退出），
        // 正在进行的出图会失败并降级为文本 —— 这是 reload 的预期代价，日志中已注明。
        synchronized(rendererLock) {
            renderers.values.forEach { runCatching { it.shutdown() } }
            renderers.clear()
            playwrightRenderer?.let { runCatching { it.shutdown() } }
            playwrightRenderer = null
        }
        // 熔断表随解析器一起重建：配置重载后应给上一次故障的端点一次新机会
        avatarCacheRef = null
        avatarFetcherRef = null
        avatarResolverRef = null

        context.log(
            LogLevel.INFO,
            "[PlumBot-Render] 主题、引擎与头像管线已重载：引擎将按新配置重建，进行中的出图可能降级为文本。"
        )
    }

    override fun metrics(): Map<String, Long> = RenderMetrics.snapshot()

    override fun shutdown() {
        renderers.values.forEach { runCatching { it.shutdown() } }
        targetLocks.clear()
        avatarCacheRef = null
        avatarFetcherRef = null
        avatarResolverRef = null
    }

    /**
     * 只反映"配置是否启用"：门控关闭或 `engine=none` 视为**主动关闭**；
     * 队列溢出、引擎不可用等属于"启用后失败"，由 [render] 的 Unavailable/Failed 表达。
     */
    override fun isImageEnabled(route: String): Boolean {
        val parsed = RenderRoute.byName(route) ?: return false
        return picEnabled(parsed) && engineFor(parsed) != ENGINE_NONE
    }

    override suspend fun render(request: RenderRequest): RenderResult {
        val route = RenderRoute.byName(request.route)
            ?: return RenderResult.Failed("未知路由: ${request.route}")

        if (!picEnabled(route)) return RenderResult.Unavailable

        val engine = engineFor(route)
        if (engine == ENGINE_NONE) return RenderResult.Unavailable

        val targetKey = request.targetKey
        if (targetKey == null) return renderWithCapacity(route, engine, request)

        val lock = targetLocks.computeIfAbsent(targetKey) { Mutex() }
        lock.lock()
        return try {
            renderWithCapacity(route, engine, request)
        } finally {
            lock.unlock()
            // 水位超限时只清理**空闲**条目。
            //
            // 原实现是 `targetLocks.clear()`：那会把其它协程正在持有的 Mutex 一并丢弃，
            // 正在等待者的锁对象与新调用者拿到的锁对象不再相同 ——
            // "同一目标串行渲染"的保证就此失效（同一目标可能并发出图、互相覆盖状态）。
            // 说明：`isLocked` 为 false 但仍有人刚排队的极端时序下仍可能被移除，
            // 代价是那一瞬间的串行性，而不是把正在执行的渲染放跑。
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
        return try {
            doRender(route, engine, request)
        } finally {
            capacity.release()
        }
    }

    private suspend fun doRender(
        route: RenderRoute,
        engine: String,
        request: RenderRequest
    ): RenderResult {
        val options = RenderOptions(timeoutMs = request.timeoutMs ?: timeoutFor(route))
        val payload = governPayload(request.payload)

        val primary = rendererFor(engine)?.takeIf { it.isSupported }
        if (primary != null) {
            val bytes = runCatching { primary.render(route.route, payload, options) }
                .onFailure {
                    context.log(LogLevel.WARN, "[PlumBot-Render] 引擎 $engine 失败: ${it.message}，尝试降级 AWT...")
                }
                .getOrNull()
            if (bytes != null && isRenderableImage(bytes)) return RenderResult.Image(bytes)
        }

        // 主引擎即 AWT 时不再重复渲染一次
        if (engine == ENGINE_AWT) {
            return RenderResult.Failed("AWT 引擎未能产出有效图片（路由 ${route.route}）")
        }

        // 兜底同样要走"按需重建"的查找（reload 后引擎表被清空，直接查表会拿不到）
        val fallback = rendererFor(ENGINE_AWT)?.takeIf { it.isSupported }
            ?: return RenderResult.Unavailable
        val fallbackBytes = runCatching { fallback.render(route.route, payload, options) }
            .onFailure { context.log(LogLevel.WARN, "[PlumBot-Render] AWT 兜底失败: ${it.message}") }
            .getOrNull()
        return if (fallbackBytes != null && isRenderableImage(fallbackBytes)) {
            RenderResult.Image(fallbackBytes)
        } else {
            RenderResult.Failed("AWT 兜底未能产出有效图片（路由 ${route.route}）")
        }
    }

    // ---------- 配置解析 ----------

    /**
     * 引擎解析：`render.views.<route>.engine` → `render.engine` → 内置默认 `awt`。
     *
     * 未知取值会**一次性 WARN** 后落到 AWT 兜底，见 [warnIfUnknownEngine]。
     * 门控见 [picEnabled]。
     */
    private fun engineFor(route: RenderRoute): String {
        val specific = context.config.getString("render", "views", route.route, "engine")
        val global = context.config.getString("render", "engine")
        val engine = (specific ?: global ?: ENGINE_AWT).lowercase()
        warnIfUnknownEngine(route, engine)
        return engine
    }

    /**
     * 未知引擎值 → **一次性 WARN** 并说明已回落 AWT。
     *
     * 为什么需要它：本方法原先只做小写化、**没有取值白名单**，于是任何拼写错误
     * （`playwrite` / `palywright`）都会**静默**走到 AWT 兜底 —— 服主以为开了高清渲染，
     * 实际一直是本地轻量图，而且没有任何线索可查。
     *
     * 去重键是**引擎取值**而非 route，因此同一个错拼只在首次出现时提示一次。
     */
    private fun warnIfUnknownEngine(route: RenderRoute, engine: String) {
        if (engine in KNOWN_ENGINES) return
        if (!engineNotices.add(engine)) return
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 未知渲染引擎 '$engine'（首次出现在路由 ${route.route}）—— " +
                "已回落 $ENGINE_AWT。可选值：${KNOWN_ENGINES.joinToString(" / ")}"
        )
    }

    /**
     * 出图门控：`render.outputs.<功能>`。
     *
     * 这是图片开关的唯一真源。`TEXT` 没有功能段（纯文本始终可用），因此直接放行；
     * `HYTALE_AVATAR` 归 `render.outputs.avatar`，其余路由各自映射到对应功能段。
     *
     * 键由内置模板保证存在，`ConfigMigrator` 也会为旧配置补齐，因此这里的布尔读取可靠。
     */
    private fun picEnabled(route: RenderRoute): Boolean {
        val gate = route.featureGate ?: return true
        return context.config.getBoolean("render", "outputs", gate)
    }

    private fun timeoutFor(route: RenderRoute): Long {
        val specific = context.config.getInteger("render", "views", route.route, "timeout_ms")
        if (specific > 0) return specific.toLong()
        val global = context.config.getInteger("render", "playwright", "render_timeout_ms")
        return if (global > 0) global.toLong() else DEFAULT_TIMEOUT_MS
    }

    private fun refreshThemeManager() {
        themeManager = buildThemeManager()
        playwrightRenderer?.updateThemeManager(themeManager)
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

    /**
     * 仅供测试：注册一个可控引擎，用于确定性地验证**队列容量**与**同目标串行**（A12）。
     *
     * 没有这个接缝就只能靠真实引擎的时序来碰运气 —— 渲染太快根本进不了"容量已满"的状态。
     */
    internal fun registerEngineForTest(renderer: ImageRenderer) {
        register(renderer)
    }

    // ---------- 载荷治理 ----------

    /**
     * 三层长度治理（基线 §5）。取代旧实现的「500 字符静默截断」。
     *
     * 注意：`data:` URL（服务端预取并合成的头像图层）**不参与文本截断**，否则会被截成坏图。
     */
    private fun governPayload(payload: Any): Any {
        val config = context.config
        val governor = PayloadGovernor(
            displayTextMax = configuredLimit(config, "display_text_max", DEFAULT_DISPLAY_TEXT_MAX),
            perLayerMaxBytes = configuredLimit(config, "per_layer_max", DEFAULT_PER_LAYER_MAX),
            binaryTotalMaxBytes = configuredLimit(config, "binary_total_max", DEFAULT_BINARY_TOTAL_MAX),
            layersMax = configuredLimit(config, "layers_max", DEFAULT_LAYERS_MAX),
            textTotalWarn = configuredLimit(config, "total_text_warn", DEFAULT_TOTAL_TEXT_WARN),
            warn = { message -> context.log(LogLevel.WARN, message) }
        )
        val governed = governor.govern(payload)
        governor.report()
        return governed ?: payload
    }

    private fun configuredLimit(config: PlatformConfig, key: String, fallback: Int): Int {
        val value = config.getInteger("render", "limits", key)
        return if (value <= 0) fallback else value
    }

    /**
     * 校验产出确实是**图片**（主引擎与 AWT 兜底走同一套校验）。
     *
     * 从"只认 PNG"放宽为"PNG 或 JPEG"：卡片图可配置为 JPEG 档（图片体积优化决策），
     * 若仍只认 PNG，JPEG 卡片会被误判为失败并触发无谓的兜底重渲染。
     */
    private fun isRenderableImage(bytes: ByteArray): Boolean {
        val mime = SafeHttp.mimeOf(bytes)
        return mime == "image/png" || mime == "image/jpeg"
    }

    private companion object {
        const val ENGINE_NONE = "none"
        const val ENGINE_AWT = "awt"
        const val ENGINE_PLAYWRIGHT = "playwright"

        /** 合法引擎取值；不在其中的一律 WARN 并回落 AWT。 */
        val KNOWN_ENGINES = setOf(ENGINE_NONE, ENGINE_AWT, ENGINE_PLAYWRIGHT)

        const val PLAYWRIGHT_CLASS = "com.microsoft.playwright.Playwright"

        /** 并发渲染数（与 Playwright 页面池规模一致）。 */
        const val CONCURRENCY = 2

        /** 允许排队的深度；超出立即降级纯文本，**不排队、不丢弃**。 */
        /** Playwright 按需装载失败的冷却时长（R17）：避免离线环境中每条消息都触发下载尝试。 */
        const val PLAYWRIGHT_LOAD_COOLDOWN_MS = 60_000L
        /** 冷却键（单引擎，固定值即可）。 */
        const val PLAYWRIGHT_LOAD_KEY = "playwright-load"
        const val QUEUE_DEPTH = 6

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
