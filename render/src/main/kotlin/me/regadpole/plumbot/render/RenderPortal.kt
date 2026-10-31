// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.render.support.RenderLimits

/**
 * 模板下载的默认 User-Agent。
 *
 * 与 `:common` 的 `HttpConstants.BROWSER_USER_AGENT` 同值；由调用方在 [RenderPortal.configure]
 * 时注入共享常量，避免常量在多处各写一份。
 */
internal const val DEFAULT_RENDER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * 渲染门户 —— `:render` 模块**对外唯一入口**。
 *
 * 其他模块**只与门户对话**，不得直接引用 `TextToImg`、`ThemeManager`、驱动等内部类型。
 * 门户保证**唯一实例**与**唯一生命周期**（平台在插件启用时 `bootstrap` 一次，禁用 / 重载时
 * `reload()` / `shutdown()`）。[render] **永不抛异常**：失败一律以 [RenderResult.Unavailable] /
 * [RenderResult.Failed] 返回，由调用方安全降级为纯文本。
 */
@PublicApi
interface RenderPortal {

    /** 当前环境是否**有可能**产出图片（用于调用方提前短路，不代表某次渲染一定成功）。 */
    val isImageCapable: Boolean

    /**
     * **预加载入口**：把"按需装载的引擎依赖"准备好，再报告能否出图。
     *
     * 与 [isImageCapable] 的区别是**顺序**：后者只是"此刻是否可用"的快照，Playwright 由
     * Libby 按需下载时，调用方可能在装载被触发之前就判定"不能出图"，永远走不到按需装载
     * 路径 —— 表现为"配了 playwright 却一直出纯文本"。内置实现先尝试装载再判定，并对失败
     * 加冷却，避免每条消息都触发一次下载尝试。
     */
    fun warmUpEngines(): Boolean = isImageCapable

    /**
     * **阻塞**准备 Playwright 运行库，返回失败原因（null = 已就绪）。
     *
     * 与 [warmUpEngines] 的分工：后者只调度、立即返回；本方法供 `/plumbot render doctor|install`
     * 使用 —— 它们必须拿到"jar 到底装没装上"的真实结论，否则会在 Libby 尚未被触发时误报
     * `PW_DEPENDENCY_MISSING`。**只允许在平台异步线程调用**：`driver-bundle` 约 190 MB，
     * 首次调用会触发 Libby 下载。默认实现视为不可用，不谎报"已就绪"。
     */
    fun ensurePlaywrightLibrary(): String? = "当前渲染门户不支持 Playwright 运行库预装载"

    /**
     * 该路由**是否按配置启用了图片**（门控 + 引擎不为 `none`）。
     *
     * 与 `render()` 返回 [RenderResult.Unavailable] 的区别：后者还包含"队列溢出、主引擎
     * 失败且 AWT 不可用"等**启用后**的失败。调用方据此把"用户主动关闭"与"启用但降级"
     * 分开计数，否则降级指标会长期虚高。
     */
    fun isImageEnabled(route: String): Boolean = true

    /**
     * 模板是否**声明实现了**该路由（`null` = 尚无依据判断）。
     *
     * 与 [isImageEnabled] 的分工：后者是**配置门控**，本方法是**模板能力**。旧版模板的
     * manifest 只声明了 `hytaleAvatar`，若继续下发新的 `avatar` 路由，Playwright 会在
     * handshake 阶段以 PW_TEMPLATE_INVALID 拒绝，而 `fallback_to_awt=true` 又会让 AWT 画一张
     * **没有头像**的文字图接住 —— 比纯文本更难察觉。调用方据此在第一次渲染前就选对路由。
     *
     * `null`：尚未读到 manifest（引擎未装载 / 未握手过）→ 按原顺序尝试；`true`：manifest
     * 明确声明，或模板不限制路由（缺 `routes` 的旧 SDK）；`false`：明确未声明 → 改走兼容路由。
     * 默认 `null`：AWT 根本不看路由，不该给出否定结论。
     */
    fun supportsRoute(route: RenderRoute): Boolean? = null

    /** 注入渲染配置（字体、下载 UA 等）。可在启动期与重载期调用。 */
    fun configure(config: RenderConfig)

    /** 重载主题与渲染配置（会同步传播到各驱动）。 */
    fun reload()

    /** 运行时指标快照（降级 / 队列拒绝 / 成功出图次数等），由业务层汇总展示。 */
    fun metrics(): Map<String, Long> = emptyMap()

    /**
     * 渲染健康计数（**分类口径的唯一真源**）。字段含义见 [RenderHealth]。
     *
     * 与 [metrics] 的区别：本方法是给 `/plumbot status` 的稳定结构，字段名不随内部键改名
     * 而变；**只有门户能准确回答**「这次失败是降级还是拒绝」「AWT 是否补图成功」，
     * 因此业务层不再自行计数。
     */
    fun renderHealth(): RenderHealth = RenderHealth.EMPTY

    /** 清零渲染健康计数（供 `/plumbot render reset`）。默认无操作。 */
    fun resetRenderHealth() {}

    /**
     * 协议字段自检摘要（`/plumbot render doctor` 用），形如
     * `protocol fields: 16 routes, mismatches: 0`。
     *
     * 前端迁出为独立仓库后，协议一致性只剩两条运行时抓手：manifest 的 protocol/routes
     * 握手，以及这里的 data 字段键集比对。
     */
    fun describeProtocolFields(): String? = null

    /** 回收全部渲染资源。 */
    fun shutdown()

    /**
     * 关闭并**等待**回收完成（有界），返回是否在超时前排空。
     *
     * 停服路径**必须**用本方法而不是 [shutdown]：[shutdown] 是 fire-and-forget（引擎拆除被
     * 丢到 Reaper 线程），若插件 disable() 立刻返回，平台随即关闭插件类加载器，而回收线程
     * 这时仍在调用 PlaywrightImpl.close() —— 线上实测抛
     * "The plugin classloader for PlumBot has thrown a zip file error."。
     *
     * @param timeoutMs 最长等待毫秒数；<= 0 表示不等待（等价于 [shutdown]）。
     */
    fun shutdownAndAwait(timeoutMs: Long = RenderLimits.DEFAULT_TEARDOWN_TIMEOUT_MS): Boolean {
        shutdown()
        return true
    }

    /**
     * 执行一次渲染。
     *
     * @return [RenderResult.Image] 成功；[RenderResult.Unavailable] 未启用/能力不足/队列溢出（可直接发纯文本）；
     *   [RenderResult.Failed] 渲染过程失败（同样应降级为纯文本）。
     */
    suspend fun render(request: RenderRequest): RenderResult

    /**
     * 服务端预取一张头像并返回 `data:` URL（带磁盘缓存）。
     *
     * 由**调用方决定来源**（绑定 QQ / 皮肤源等），门户只负责：出口白名单校验、体积上限、
     * 磁盘缓存与 data URL 编码 —— 从而让浏览器彻底不必跨域取图。
     *
     * @param cacheKey 缓存键（如 `qq:123456`）
     * @param url 必须为 https，且主机需在出口白名单内
     * @return `data:image/png;base64,…`；不可用时返回 `null`（调用方应省略该字段或走纯文本）
     */
    suspend fun fetchAvatarAsDataUrl(cacheKey: String, url: String): String?

    /**
     * 解析玩家的 Minecraft 皮肤头像：**五级漏斗的前三级**（皮肤插件 → GameProfile → 正版源）
     * + 服务端裁头 + 磁盘缓存。
     *
     * @return `data:image/png;base64,…`；三级全部未命中时返回 `null`，
     *         调用方应继续第 ④ 级（绑定 QQ）与第 ⑤ 级（前端占位图）。
     */
    suspend fun resolveSkinAvatar(playerName: String): String?

    /**
     * 把**有序图层**合成成单张成品头像，返回 `data:image/png;base64,…`（带磁盘缓存）。
     *
     * 图层由调用方提供（平台资产 / 服主自定义目录 / 网络），门户只负责合成、缓存与编码。
     * 图层全部无效时返回 `null`，调用方应回退到 QQ 头像或占位图。
     */
    suspend fun composeAvatar(request: AvatarComposeRequest): String?
}

/**
 * 文本可读性规则（**唯一真源的运行时载体**）。
 *
 * 默认值与 `render-protocol.json` 的 `text` 节点、`config.yml` 的 `render.text` 由
 * `checkRenderProtocol` 机械校验一致；门户在 `configure()` 时注入引擎。
 */
@PublicApi
data class RenderTextStyle(
    val baseColor: String = "#1E2430",
    val textColor: String = "#E6E6E6",
    val contrastThreshold: Double = 4.5,
    val autoOutline: Boolean = true,
    val maxLineWidth: Int = 960
)

/** 渲染配置；`fontFile` 为空时 AWT 使用系统默认无衬线字体。 */
@PublicApi
data class RenderConfig(
    val fontFile: File? = null,
    val userAgent: String? = null,
    val textStyle: RenderTextStyle = RenderTextStyle()
)

/**
 * 渲染请求。
 *
 * @param route 见 [RenderRoute]
 * @param payload 结构化载荷；通用文本走 [TextPayload]
 * @param targetKey 目标标识（如 `group:123456`）。同一目标串行渲染，保证消息顺序
 * @param timeoutMs 覆盖该次渲染的超时（为空则由该 route 的配置决定）
 */
@PublicApi
data class RenderRequest(
    val route: String,
    val payload: Any,
    val targetKey: String? = null,
    val timeoutMs: Long? = null
)

/** 通用文本卡片载荷：用于聊天转发、控制台回显、错误提示等非结构化文本。 */
@PublicApi
data class TextPayload(
    val body: String,
    val title: String? = null,
    /** 是否把 `&` 也视作色码前缀（控制台回显为 `&`-legacy，需要开启）。 */
    val ampersandCodes: Boolean = false
)

/**
 * 统一渲染卡片：所有路由共用同一份"文本 + 可选标题 + 结构化数据"载荷。
 *
 * [text] 是**唯一**的纯文本真源（`§` 色码），图片不可用 / 引擎关闭 / 发送失败时直接
 * 发给用户，保证文字版与图片版内容一致。[data] 只携带模板需要但正文里没有的结构化字段
 * （玩家列表、TPS 数组等），前端视图不得硬编码中文文案；[title] 由业务层按当前语言给出。
 */
@PublicApi
data class RenderCard(
    val text: String,
    val title: String? = null,
    val data: Map<String, Any?> = emptyMap()
) {
    /**
     * 标题与正文的合成正文，供**纯文本降级**与 AWT 出图共用。
     *
     * 模板会把 [title] 单独排版，AWT 没有这套版式：只画 [text] 时 status 的服务器名、
     * kick/advancement 的标题就只存在于图片侧。标题已作为正文首行出现时不重复拼接。
     */
    val displayText: String
        get() {
            val heading = title?.takeIf { it.isNotBlank() } ?: return text
            val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine == heading.trim() || firstLine.startsWith(heading.trim())) return text
            return heading + "\n" + text
        }
}

@PublicApi
sealed interface RenderResult {
    /** 渲染成功，产物为 PNG 字节。 */
    data class Image(val png: ByteArray) : RenderResult

    /** 未产出图片，但**不是错误**（引擎关闭、平台不支持、队列溢出）—— 调用方直接发纯文本。 */
    data object Unavailable : RenderResult

    /** 渲染失败（超时、模板缺失、驱动异常）—— 调用方降级发纯文本。 */
    data class Failed(val reason: String) : RenderResult
}

/** 平台层实现：按需下载并装载 Playwright（Libby）。`:render` 只声明 SPI，由平台插件注入实现。 */
@PublicApi
interface RenderLibraryLoader {
    /**
     * 首次调用时下载并装载 Playwright；成功返回 true。
     *
     * 必须装载**三件套**（`driver` / `driver-bundle` / `playwright`，均为 `transitive=false`）：
     * 只装 `playwright` 会在 `Playwright.create()` 时找不到 `DriverJar` 与原生 node；
     * 打开传递解析则会把 JUnit 拖进服务端。
     */
    fun ensurePlaywright(): Boolean

    /** 最近一次 [ensurePlaywright] 失败的原因；成功或尚未尝试时为 null（doctor 原样展示）。 */
    fun lastFailure(): String? = null
}

/**
 * 路由注册表 —— 图片开关统一映射到 `render.outputs.<功能>`。
 *
 * @param route 路由名（同时是前端模板的视图路径）
 * @param featureGate 该路由对应的功能段名；null 表示无图卡开关
 * @param preferredEngine 用户未显式配置引擎时的首选引擎（显式配置始终优先），头像类路由
 *   必须走 playwright —— AWT 只画正文，画不出皮肤 / 头像图层。
 *
 * 枚举条目必须保持 `NAME("route", "gate")` 两个位置参数的字面形状：
 * `build.gradle.kts` 的正则从这里提取路由清单并与 `render-protocol.json` 双向比对。
 * 需要额外属性时用条目体覆写，不要加第三个位置参数。
 */
@PublicApi
enum class RenderRoute(
    val route: String,
    val featureGate: String?,
    open val preferredEngine: String? = null,
) {
    STATUS("status", "status"),
    PLAYER_LIST("playerList", "list"),
    WHITELIST("whitelist", "bind"),
    BIND_LIST("bindList", "bind"),
    HELP("help", "help"),
    LOAD("load", "load"),
    UNLOAD("unload", "load"),
    MESSAGE("message", "message"),
    JOIN_AND_LEAVE("joinAndLeave", "joinAndLeave"),
    CHANGE_SERVER("changeServer", "joinAndLeave"),
    DEATH("death", "death"),
    ADVANCEMENT("advancement", "advancement"),
    KICK("kick", "kick"),
    CONSOLE("console", "remote_console"),
    TEXT("text", "text"),

    /** 头像卡（平台无关）：data 额外带 `platform`，供前端按平台挑皮肤/头像图层。 */
    AVATAR("avatar", "avatar") {
        override val preferredEngine: String? get() = "playwright"
    },

    /** 旧头像卡路由，保留兼容旧前端模板；插件已改发 [AVATAR]，不再主动下发。 */
    HYTALE_AVATAR("hytaleAvatar", "avatar") {
        override val preferredEngine: String? get() = "playwright"
    };

    companion object {
        fun byName(name: String): RenderRoute? =
            entries.firstOrNull { it.route.equals(name, ignoreCase = true) }
    }
}
