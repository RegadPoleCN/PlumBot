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

import me.regadpole.plumbot.api.PublicApi
import java.io.File

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
 * 设计约定（基线 §3.6 / D-13）：
 * - 其他模块**只与门户对话**，不得直接引用 `TextToImg`、`ThemeManager`、驱动等内部类型；
 * - 门户同时保证**唯一实例**与**唯一生命周期**（由平台在插件启用时 `bootstrap` 一次，
 *   在禁用/重载时 `reload()` / `shutdown()`）；
 * - [render] **永不抛异常**：任何失败都以 [RenderResult.Unavailable] / [RenderResult.Failed]
 *   返回，由调用方安全降级为纯文本。
 */
@PublicApi
interface RenderPortal {

    /** 当前环境是否**有可能**产出图片（用于调用方提前短路，不代表某次渲染一定成功）。 */
    val isImageCapable: Boolean

    /**
     * **预加载入口**（R17）：把"按需装载的引擎依赖"准备好，再报告能否出图。
     *
     * 与 [isImageCapable] 的区别是**顺序**：后者只是"此刻是否可用"的快照，
     * 于是当引擎依赖尚未装载（Playwright 由 Libby 按需下载）时，调用方可能**在装载被触发之前**
     * 就判定"不能出图"，从而永远走不到那条按需装载路径 —— 表现为"配了 playwright 却一直出纯文本"。
     *
     * 默认实现退化为 [isImageCapable]（保持既有实现者不变）；内置实现会先尝试装载再判定，
     * 并对失败加冷却，避免每条消息都触发一次下载尝试。
     */
    fun warmUpEngines(): Boolean = isImageCapable

    /** 注入渲染配置（字体、下载 UA 等）。可在启动期与重载期调用。 */
    fun configure(config: RenderConfig)

    /** 重载主题与渲染配置（会同步传播到各驱动）。 */
    fun reload()

    /**
     * 运行时指标快照（渲染降级 / 队列拒绝 / 成功出图次数等）。
     *
     * 默认空实现，便于既有驱动不实现它；**渲染指标必须自持**（`:render` 不依赖 `:common`），
     * 由业务层汇总展示（如 `/plumbot status`）。
     */
    fun metrics(): Map<String, Long> = emptyMap()

    /** 回收全部渲染资源。 */
    fun shutdown()

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
 * 默认值与 `render-protocol.json` 的 `text` 节点、`config.yml` 的 `feature.img.text` 三者
 * 由 `checkRenderProtocol` 机械校验一致；门户在 `configure()` 时注入引擎。
 *
 * @param baseColor 底板色（`#RRGGBB`）
 * @param textColor 默认文字色（`#RRGGBB`）
 * @param contrastThreshold 低于该对比度时自动描边（WCAG 对比度）
 * @param autoOutline 是否启用自动描边
 */
@PublicApi
data class RenderTextStyle(
    val baseColor: String = "#1E2430",
    val textColor: String = "#E6E6E6",
    val contrastThreshold: Double = 4.5,
    val autoOutline: Boolean = true
)

/**
 * 渲染配置。
 *
 * @param fontFile AWT 引擎使用的 TTF 字体文件（为空则使用系统默认无衬线字体）
 * @param userAgent 模板下载使用的 User-Agent
 * @param textStyle 文本可读性规则（由门户注入引擎，缺省用清单默认值）
 */
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

@PublicApi
sealed interface RenderResult {
    /** 渲染成功，产物为 PNG 字节。 */
    data class Image(val png: ByteArray) : RenderResult

    /** 未产出图片，但**不是错误**（引擎关闭、平台不支持、队列溢出）—— 调用方直接发纯文本。 */
    data object Unavailable : RenderResult

    /** 渲染失败（超时、模板缺失、驱动异常）—— 调用方降级发纯文本。 */
    data class Failed(val reason: String) : RenderResult
}

/**
 * 平台层实现：按需下载并装载 Playwright（Libby）。
 *
 * `:render` 只声明该 SPI，不依赖任何平台或 Libby 类型；由平台插件注入实现。
 */
@PublicApi
interface RenderLibraryLoader {
    /** 首次调用时下载并装载 Playwright；成功返回 true。 */
    fun ensurePlaywright(): Boolean
}

/**
 * 路由注册表 —— 取代原先散落在各处的 `feature.<x>.pic` 判断。
 *
 * @param route 路由名（同时是前端模板的视图路径）
 * @param legacyGate 迁移期的旧配置节点名（`feature.<legacyGate>.pic`），用于兼容既有配置
 */
@PublicApi
enum class RenderRoute(val route: String, val legacyGate: String?) {
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
    TEXT("text", null),
    HYTALE_AVATAR("hytaleAvatar", null);

    companion object {
        fun byName(name: String): RenderRoute? =
            entries.firstOrNull { it.route.equals(name, ignoreCase = true) }
    }
}
