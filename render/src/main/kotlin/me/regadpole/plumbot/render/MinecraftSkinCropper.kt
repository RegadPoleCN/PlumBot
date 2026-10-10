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

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Minecraft 皮肤 → **头部头像**的服务端裁切（基线 D-9）。
 *
 * 为什么放在服务端：浏览器端的皮肤嗅探（`src.includes('textures.minecraft.net')`）
 * 在 `data:` URL 下永远不命中，会把整张 64×64 皮肤当头像显示；
 * 且前端裁切需要把原始皮肤传给客户端，等于把"每张卡片 N 次网络往返"留在渲染超时窗口内。
 *
 * 皮肤布局（64×64 现代版）：
 * ```
 * 头部正面  (8, 8)  8×8
 * 头部外层  (40, 8) 8×8   ← 帽子/头发等叠加层，按 alpha 合成
 * ```
 * 64×32 为旧版布局，无外层；128×128 为 2 倍高清皮肤，坐标同比放大。
 */
internal object MinecraftSkinCropper {

    private const val FACE_X = 8
    private const val FACE_Y = 8
    private const val FACE_SIZE = 8
    private const val HAT_X = 40

    /**
     * 裁切并放大为 [targetSize] 见方的头像。
     *
     * @return 头像图；皮肤尺寸非法或整块面部全透明时返回 null（调用方应进入下一级）
     */
    fun cropHead(skin: BufferedImage, targetSize: Int = 128): BufferedImage? {
        val unit = resolveScale(skin) ?: return null
        val faceSize = FACE_SIZE * unit

        val face = BufferedImage(faceSize, faceSize, BufferedImage.TYPE_INT_ARGB)
        copyRegion(skin, FACE_X * unit, FACE_Y * unit, face, faceSize)

        // 外层只在现代布局（正方形且 ≥64）中存在
        if (skin.height == skin.width && skin.width >= 64) {
            compositeRegion(skin, HAT_X * unit, FACE_Y * unit, face, faceSize)
        }

        if (isFullyTransparent(face)) return null
        return scaleNearest(face, targetSize.coerceAtLeast(FACE_SIZE))
    }

    /** 从 PNG 字节裁头；解码失败或尺寸超预算返回 null（A15：解码前先校验像素预算）。 */
    fun cropHead(png: ByteArray, targetSize: Int = 128): ByteArray? {
        if (ImageDimensions.exceedsPixelBudget(png)) return null
        val image = runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull() ?: return null
        val head = cropHead(image, targetSize) ?: return null
        val out = ByteArrayOutputStream()
        ImageIO.write(head, "png", out)
        return out.toByteArray()
    }

    /** 返回每个"皮肤像素"对应多少个图像像素（1 或 2）；无法识别返回 null。 */
    private fun resolveScale(skin: BufferedImage): Int? = when {
        skin.width == 64 && (skin.height == 64 || skin.height == 32) -> 1
        skin.width == 128 && skin.height == 128 -> 2
        else -> null
    }

    private fun copyRegion(src: BufferedImage, srcX: Int, srcY: Int, dst: BufferedImage, size: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                dst.setRGB(x, y, src.getRGB(srcX + x, srcY + y))
            }
        }
    }

    /** 按 alpha 把叠加层合成到面部之上（源在下方，叠加层在上方 → SRC_OVER）。 */
    private fun compositeRegion(src: BufferedImage, srcX: Int, srcY: Int, dst: BufferedImage, size: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val over = src.getRGB(srcX + x, srcY + y)
                val overA = (over ushr 24) and 0xFF
                if (overA == 0) continue

                val under = dst.getRGB(x, y)
                if (overA == 255) {
                    dst.setRGB(x, y, over)
                    continue
                }
                val underA = (under ushr 24) and 0xFF
                val a = overA + underA * (255 - overA) / 255
                if (a == 0) {
                    dst.setRGB(x, y, 0)
                    continue
                }
                fun channel(overC: Int, underC: Int): Int =
                    (overC * overA + underC * underA * (255 - overA) / 255) / a

                val r = channel((over ushr 16) and 0xFF, (under ushr 16) and 0xFF)
                val g = channel((over ushr 8) and 0xFF, (under ushr 8) and 0xFF)
                val b = channel(over and 0xFF, under and 0xFF)
                dst.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }

    private fun isFullyTransparent(image: BufferedImage): Boolean {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (((image.getRGB(x, y) ushr 24) and 0xFF) != 0) return false
            }
        }
        return true
    }

    /** 像素风放大必须用最近邻，否则 8×8 的脸会被插值糊掉。 */
    private fun scaleNearest(source: BufferedImage, target: Int): BufferedImage {
        val out = BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until target) {
            val sy = y * source.height / target
            for (x in 0 until target) {
                val sx = x * source.width / target
                out.setRGB(x, y, source.getRGB(sx, sy))
            }
        }
        return out
    }
}
