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

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi

@StableApi
interface PlatformPlayerService {
    fun kickPlayer(name: String)

    fun listPlayers(): List<String>

    fun listPlayerString(): String

    /**
     * 返回玩家的平台级唯一标识（字符串形式），未知返回 `null`。
     *
     * 默认实现返回 `null`，各平台按自身能力覆写。绑定流程在玩家进服时用它补写
     * `player_uuid`，从而支撑需要 UUID 的能力（正版皮肤解析、UUID 型头像源等）。
     */
    fun getPlayerUuid(name: String): String? = null

    /**
     * 返回该平台内置的**头像图层原始数据**（自底向上），无此能力时返回空列表。
     *
     * 刻意保持**平台中立**（只有字节/混合方式/颜色，不含任何渲染类型）：
     * 由上层把它交给渲染门户合成成单张成品图，`:api` 因此不必依赖 `:render`。
     */
    fun resolveAvatarLayers(playerName: String): List<PlatformAvatarLayer> = emptyList()

    // ------------------------------------------------------------------
    // 皮肤来源（五级漏斗的 L1–L3；L4 绑定 QQ / L5 占位由上层处理）
    //
    // L1/L2 统一返回 **base64 纹理属性**（即 GameProfile 里 `textures` 属性的 value），
    // L3 返回正版 UUID —— 二者最后都会走到同一段"base64 → JSON → textures.SKIN.url"的解析，
    // 该解析集中在渲染侧的 AvatarResolver，平台层因此**不需要任何 JSON 处理**。
    //
    // 三者默认返回 null：不实现任何一级也不会破坏编译，漏斗自然退化到 L4。
    // ------------------------------------------------------------------

    /**
     * ① 第三方皮肤插件（如 SkinsRestorer）提供的纹理属性（base64）。
     *
     * 实现方必须用**反射**调用插件 API，不得产生硬依赖 —— 插件缺失时返回 null 即可。
     */
    fun skinTextureFromPlugin(playerName: String): String? = null

    /**
     * ② 内存中 GameProfile 的纹理属性（base64，正版登录或插件注入后可见）。
     *
     * Bukkit 侧注意：读取 profile 可能触发一次网络请求，**绝不可在主线程调用**。
     * 渲染侧已在 `Dispatchers.IO` 中调用本方法，实现方无需自行切线程。
     */
    fun skinTextureFromProfile(playerName: String): String? = null

    /**
     * ③ 正版 UUID（用于向 Mojang 会话服务查询皮肤）。
     *
     * 与 [getPlayerUuid] 的区别：这里只应在**确认是正版账号**时返回 UUID。
     * 离线/盗版服返回的 UUID 会让会话服务查不到，虽然最终也会自然降级到 L4，
     * 但会产生一次无谓的外部请求，因此实现方应尽量先判断正版性。
     */
    fun premiumUuid(playerName: String): String? = null
}

/**
 * 平台内置头像图层的原始数据。
 *
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
