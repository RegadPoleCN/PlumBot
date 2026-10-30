// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.PublicApi

/** 图层混合方式。 */
@PublicApi
enum class AvatarBlend {
    /** 直接叠加（带 alpha）。 */
    NORMAL,

    /** 按 `color` 做逐通道正片叠底（Hytale 的"灰度贴图 + 染色"模型）。 */
    MULTIPLY
}

/**
 * 头像图层。
 *
 * @param png 图层 PNG 字节（来源由调用方决定：平台资产、服主自定义目录、网络等）
 * @param color `#RRGGBB`，仅 [AvatarBlend.MULTIPLY] 使用
 */
@PublicApi
class AvatarLayer(
    val png: ByteArray,
    val blend: AvatarBlend = AvatarBlend.NORMAL,
    val color: String? = null
) {
    override fun toString(): String =
        "AvatarLayer(png=${png.size}B, blend=$blend, color=$color)"
}

/**
 * 头像合成请求。**有序图层只是服务端内部中间表示**：线上契约是单张成品图的 `data:` URL，
 * 浏览器端因此无需 canvas、无需跨域、无需模式嗅探。
 * @param cacheKey 缓存键（如 `hytale:Steve`）；为空表示不缓存
 * @param layers 自底向上的图层顺序
 */
@PublicApi
data class AvatarComposeRequest(
    val cacheKey: String?,
    val layers: List<AvatarLayer>,
    val size: Int = 128
)
