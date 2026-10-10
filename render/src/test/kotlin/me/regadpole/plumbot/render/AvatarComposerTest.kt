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

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 有序图层服务端合成的契约测试（基线 §7.3 / §8.2）。
 *
 * 线上契约收敛为**单张成品图**：浏览器端因此不需要 canvas、不需要跨域、不需要模式嗅探。
 */
class AvatarComposerTest {

    private fun solidPng(argb: Int, size: Int = 16): ByteArray {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until size) for (x in 0 until size) image.setRGB(x, y, argb)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun centerPixel(png: ByteArray): Int {
        val image = ImageIO.read(ByteArrayInputStream(png))
        return image.getRGB(image.width / 2, image.height / 2)
    }

    private fun rgba(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `单层不透明图层原样保留`() {
        val result = AvatarComposer.compose(
            AvatarComposeRequest(cacheKey = null, layers = listOf(AvatarLayer(solidPng(rgba(255, 255, 0, 0)))))
        )
        assertNotNull(result)
        assertEquals(rgba(255, 255, 0, 0), centerPixel(result), "应输出纯红")
    }

    @Test
    fun `正片叠底按颜色缩放且保留 alpha`() {
        // 基色 #FFFFFF 的图层，按 50% 灰做 multiply → 128 灰
        val layer = AvatarLayer(solidPng(rgba(255, 255, 255, 255)), AvatarBlend.MULTIPLY, "#808080")
        val result = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(layer)))
        )

        val pixel = centerPixel(result)
        assertEquals(255, (pixel ushr 24) and 0xFF, "alpha 应保留")
        assertEquals(128, (pixel ushr 16) and 0xFF, "R 应为 255*128/255≈128")
        assertEquals(128, (pixel ushr 8) and 0xFF)
        assertEquals(128, pixel and 0xFF)
    }

    @Test
    fun `正片叠底不污染全透明像素`() {
        val layer = AvatarLayer(solidPng(0x00000000), AvatarBlend.MULTIPLY, "#FF0000")
        val result = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(layer)))
        )
        assertEquals(0, (centerPixel(result) ushr 24) and 0xFF, "透明像素应保持透明")
    }

    @Test
    fun `图层顺序自底向上生效`() {
        val bottom = AvatarLayer(solidPng(rgba(255, 0, 0, 255))) // 红
        val top = AvatarLayer(solidPng(rgba(255, 0, 0, 255))) // 红（不透明，最终覆盖）

        val result = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(bottom, top)))
        )
        assertEquals(rgba(255, 0, 0, 255), centerPixel(result))

        // 反转顺序、顶层改为蓝色 → 蓝色胜出
        val blueTop = AvatarLayer(solidPng(rgba(255, 0, 0, 255)))
        val blueBottom = AvatarLayer(solidPng(rgba(255, 0, 0, 255)))
        val reversed = assertNotNull(
            AvatarComposer.compose(
                AvatarComposeRequest(cacheKey = null, layers = listOf(blueBottom, blueTop))
            )
        )
        assertEquals(rgba(255, 0, 0, 255), centerPixel(reversed))
    }

    @Test
    fun `空图层与全无效图层返回 null`() {
        assertNull(AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = emptyList())))

        val garbage = AvatarLayer(byteArrayOf(1, 2, 3, 4))
        assertNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(garbage))),
            "无法解码的图层应被跳过；全部无效时返回 null 以便调用方回退"
        )
    }

    @Test
    fun `无效图层被跳过而其余图层正常合成`() {
        val garbage = AvatarLayer(byteArrayOf(9, 9, 9))
        val good = AvatarLayer(solidPng(rgba(255, 0, 255, 0)))
        val result = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(garbage, good)))
        )
        assertEquals(rgba(255, 0, 255, 0), centerPixel(result))
    }

    @Test
    fun `非法颜色不参与染色但图层仍然绘制`() {
        val layer = AvatarLayer(solidPng(rgba(255, 255, 255, 255)), AvatarBlend.MULTIPLY, "not-a-color")
        val result = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(layer)))
        )
        assertEquals(rgba(255, 255, 255, 255), centerPixel(result), "颜色非法时应原样绘制")
    }

    @Test
    fun `输出尺寸按请求缩放并被钳制`() {
        val layer = AvatarLayer(solidPng(rgba(255, 0, 0, 255)))
        val small = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(layer), size = 32))
        )
        val image = ImageIO.read(ByteArrayInputStream(small))
        assertEquals(32, image.width)
        assertEquals(32, image.height)

        val clamped = assertNotNull(
            AvatarComposer.compose(AvatarComposeRequest(cacheKey = null, layers = listOf(layer), size = 99999))
        )
        assertTrue(ImageIO.read(ByteArrayInputStream(clamped)).width <= 512, "尺寸应被钳制到上限")
    }

    @Test
    fun `颜色解析支持带井号与裸十六进制`() {
        assertEquals(Color(0x1E2430), AvatarComposer.parseColor("#1E2430"))
        assertEquals(Color(0x1E2430), AvatarComposer.parseColor("1E2430"))
        assertEquals(Color(0xFF0000), AvatarComposer.parseColor(" #FF0000 "))
        assertNull(AvatarComposer.parseColor(null))
        assertNull(AvatarComposer.parseColor("#12345"))
        assertNull(AvatarComposer.parseColor("#GGGGGG"))
    }
}
