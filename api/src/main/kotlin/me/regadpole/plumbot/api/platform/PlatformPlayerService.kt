// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi

@StableApi
interface PlatformPlayerService {
    fun kickPlayer(name: String)

    fun listPlayers(): List<String>

    fun listPlayerString(): String

    /**
     * 返回玩家的平台级唯一标识（字符串形式），未知返回 `null`（默认实现返回 `null`）。
     * 绑定流程在玩家进服时用它补写 `player_uuid`，以支撑需要 UUID 的能力（正版皮肤解析等）。
     */
    fun getPlayerUuid(name: String): String? = null

    /**
     * 返回该平台内置的**头像图层原始数据**（自底向上），无此能力时返回空列表。
     * 刻意保持**平台中立**（只有字节/混合方式/颜色，不含渲染类型）：由上层交给渲染门户
     * 合成单张成品图，`:api` 因此不必依赖 `:render`。
     */
    fun resolveAvatarLayers(playerName: String): List<PlatformAvatarLayer> = emptyList()

    // 皮肤来源漏斗（五级）：本接口只承担 ①②③；④ 平台原生图层（[resolveAvatarLayers]）、
    // ⑤ 绑定 QQ 头像与占位图由上层处理。①② 返回 **base64 纹理属性**（GameProfile `textures`
    // 属性 value），③ 返回正版 UUID，三者都走同一段"base64 → JSON → textures.SKIN.url"解析
    // （集中在渲染侧 AvatarResolver），平台层**不需要任何 JSON 处理**；默认 null 即让漏斗继续下降。

    /**
     * ① 第三方皮肤插件（如 SkinsRestorer）提供的纹理属性（base64）。
     * 实现方必须用**反射**调用插件 API，不得产生硬依赖 —— 插件缺失时返回 null。
     */
    fun skinTextureFromPlugin(playerName: String): String? = null

    /**
     * ② 内存中 GameProfile 的纹理属性（base64，正版登录或插件注入后可见）。
     * 读取 profile 可能触发网络请求，**绝不可在主线程调用**；渲染侧已在
     * `Dispatchers.IO` 中调用，实现方无需自行切线程。
     */
    fun skinTextureFromProfile(playerName: String): String? = null

    /**
     * ③ 正版 UUID（用于向 Mojang 会话服务查询皮肤），与 [getPlayerUuid] 不同：只应在
     * **确认是正版账号**时返回。离线服的 UUID 查不到，会白多一次外部请求并降级到 ④。
     */
    fun premiumUuid(playerName: String): String? = null
}

/**
 * 平台内置头像图层的原始数据。
 * @param png 图层 PNG 字节
 * @param blend 混合方式：`normal` 或 `multiply`
 * @param color `#RRGGBB`，仅 `multiply` 使用
 */
@StableApi
class PlatformAvatarLayer(
    val png: ByteArray,
    val blend: String = "normal",
    val color: String? = null
)
