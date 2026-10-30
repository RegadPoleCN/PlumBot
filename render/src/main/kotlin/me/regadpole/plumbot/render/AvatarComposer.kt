// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 有序图层 → 单张成品头像的**服务端合成器**：线上契约收敛为**一张图**。
 *
 * 为什么在服务端：浏览器不必跨域取图（这才可能移除 `--disable-web-security`），
 * 渲染耗时与外部服务抖动解耦（配合磁盘缓存），组队与 Hytale 也共用一套实现。
 *
 * NORMAL 按 alpha 直接叠加；MULTIPLY 先按 `color` 逐通道正片叠底（保留 alpha）再叠加 ——
 * 手写逐像素而非依赖 `BlendingMode`，以免受 JDK / 平台实现差异影响。
 */
internal object AvatarComposer {

    /** 合成结果；图层全部无效时返回 null（调用方应回退到 QQ 头像或占位图）。 */
    fun compose(request: AvatarComposeRequest): ByteArray? {
        val size = request.size.coerceIn(MIN_SIZE, MAX_SIZE)
        val canvas = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        var drawn = 0
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            for (layer in request.layers) {
                val decoded = decode(layer.png) ?: continue
                val prepared = when (layer.blend) {
                    AvatarBlend.NORMAL -> decoded
                    AvatarBlend.MULTIPLY -> parseColor(layer.color)?.let { tint(decoded, it) } ?: decoded
                }
                g.drawImage(prepared, 0, 0, size, size, null)
                drawn++
            }
        } finally {
            g.dispose()
        }

        if (drawn == 0) return null

        val out = ByteArrayOutputStream()
        ImageIO.write(canvas, "png", out)
        return out.toByteArray()
    }

    /** 解码前先做**像素尺寸校验**：体积上限拦不住解压炸弹 —— 几百 KB 的 PNG 可声明
     *  50000×50000，`ImageIO.read` 会按声明尺寸分配约 10GB 缓冲。 */
    private fun decode(bytes: ByteArray): BufferedImage? {
        if (ImageDimensions.exceedsPixelBudget(bytes)) return null
        return runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
    }

    /** 逐通道正片叠底，保留原 alpha。 */
    private fun tint(source: BufferedImage, color: Color): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val factorR = color.red / 255.0
        val factorG = color.green / 255.0
        val factorB = color.blue / 255.0

        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                val argb = source.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                if (a == 0) {
                    result.setRGB(x, y, 0)
                    continue
                }
                val r = (((argb ushr 16) and 0xFF) * factorR).toInt().coerceIn(0, 255)
                val g = (((argb ushr 8) and 0xFF) * factorG).toInt().coerceIn(0, 255)
                val b = ((argb and 0xFF) * factorB).toInt().coerceIn(0, 255)
                result.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return result
    }

    /** 解析 `#RRGGBB` / `RRGGBB`；非法返回 null。 */
    internal fun parseColor(raw: String?): Color? {
        val hex = raw?.trim()?.removePrefix("#") ?: return null
        if (hex.length != 6) return null
        val value = hex.toIntOrNull(16) ?: return null
        return Color(value)
    }

    private const val MIN_SIZE = 8
    private const val MAX_SIZE = 512
}
