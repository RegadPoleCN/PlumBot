// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.avatar.ImageDimensions

/**
 * **解码前的像素尺寸校验**，防解压炸弹：体积上限拦不住"几百 KB 却声明 50000×50000"的图片。
 */
class ImageDimensionsTest {

    private fun encode(width: Int, height: Int, format: String): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, format, out)
        return out.toByteArray()
    }

    @Test
    fun `reads png dimensions without decoding pixels`() {
        assertEquals(37 to 19, ImageDimensions.read(encode(37, 19, "png")))
    }

    @Test
    fun `reads jpeg dimensions`() {
        // JPEG 不支持 alpha，构造时用 RGB
        val image = BufferedImage(23, 11, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)

        assertEquals(23 to 11, ImageDimensions.read(out.toByteArray()))
    }

    @Test
    fun `rejects a png that declares a bomb sized canvas`() {
        // 注意：**不能真的生成 20000×20000 的 BufferedImage**（那会先把自己 OOM 掉，测试进程直接崩）。
        // 解压炸弹的真实形态正是"字节很小、头部声明的尺寸极大"——这里直接改写 IHDR 的宽高字段。
        val bomb = encode(8, 8, "png").copyOf()
        writeBigEndian(bomb, 16, 20_000)
        writeBigEndian(bomb, 20, 20_000)

        assertTrue(bomb.size < 1024, "构造出的样本必须是小字节数（模拟真实炸弹）")
        assertTrue(ImageDimensions.exceedsPixelBudget(bomb), "声明 20000×20000 的 PNG 必须被拒（解压炸弹）")
    }

    private fun writeBigEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 24) and 0xFF).toByte()
        target[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        target[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 3] = (value and 0xFF).toByte()
    }

    @Test
    fun `accepts a normal avatar sized image`() {
        val ok = encode(128, 128, "png")
        assertFalse(ImageDimensions.exceedsPixelBudget(ok), "128×128 属正常头像尺寸，不得误杀")
    }

    @Test
    fun `unknown or truncated headers pass the budget check and are left to the size limit`() {
        assertNull(ImageDimensions.read(byteArrayOf(1, 2, 3)), "无法识别的头部应返回 null")
        assertFalse(
            ImageDimensions.exceedsPixelBudget(byteArrayOf(1, 2, 3)),
            "尺寸未知时不得误判为超限（由体积上限兜底）"
        )
    }
}
