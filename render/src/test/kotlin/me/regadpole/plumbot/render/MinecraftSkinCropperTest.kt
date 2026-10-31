// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.avatar.MinecraftSkinCropper

/**
 * 皮肤裁头测试：裁切在服务端完成 —— 前端靠 URL 字符串嗅探决定是否裁切，
 * 而 data URL 下永不命中，会把整张 64×64 皮肤当头像发出去。
 */
class MinecraftSkinCropperTest {

    private fun rgba(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun blankSkin(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    private fun fill(image: BufferedImage, x: Int, y: Int, size: Int, argb: Int) {
        for (dy in 0 until size) for (dx in 0 until size) image.setRGB(x + dx, y + dy, argb)
    }

    private fun toPng(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `64x64 皮肤裁出面部并放大到目标尺寸`() {
        val skin = blankSkin(64, 64)
        // 注意顺序：先铺全透明底色，再画面部（反过来会把面部覆盖掉）
        fill(skin, 0, 0, 64, rgba(0, 0, 0, 0))
        fill(skin, 8, 8, 8, rgba(255, 200, 100, 50))

        val head = assertNotNull(MinecraftSkinCropper.cropHead(skin, 128))
        assertEquals(128, head.width)
        assertEquals(128, head.height)
        assertEquals(rgba(255, 200, 100, 50), head.getRGB(64, 64), "中心应是面部颜色")
    }

    @Test
    fun `帽子外层按 alpha 合成`() {
        val skin = blankSkin(64, 64)
        // rgba(a, r, g, b)：面部 = 不透明红，外层 = a=128 半透明蓝
        fill(skin, 8, 8, 8, rgba(255, 255, 0, 0))
        fill(skin, 40, 8, 8, rgba(128, 0, 0, 255))

        val head = assertNotNull(MinecraftSkinCropper.cropHead(skin, 8))
        val pixel = head.getRGB(4, 4)
        val b = pixel and 0xFF
        val r = (pixel ushr 16) and 0xFF

        assertEquals(128, b, "蓝色外层应被合成进来")
        // 整数逐通道合成存在 ±1 的取整误差
        assertTrue(kotlin.math.abs(r - 127) <= 1, "红色面部应按 alpha 保留约一半，实际 $r")
        assertEquals(255, (pixel ushr 24) and 0xFF, "alpha 应为不透明")
    }

    @Test
    fun `64x32 旧版布局没有外层`() {
        val skin = blankSkin(64, 32)
        fill(skin, 8, 8, 8, rgba(255, 0, 255, 0)) // 面部：绿
        fill(skin, 40, 8, 8, rgba(255, 255, 255, 255))

        val head = assertNotNull(MinecraftSkinCropper.cropHead(skin, 8))
        assertEquals(rgba(255, 0, 255, 0), head.getRGB(4, 4), "旧版布局不应读取外层区域")
    }

    @Test
    fun `128x128 高清皮肤按 2 倍坐标取样`() {
        val skin = blankSkin(128, 128)
        fill(skin, 16, 16, 16, rgba(255, 10, 20, 30))

        val head = assertNotNull(MinecraftSkinCropper.cropHead(skin, 8))
        assertEquals(rgba(255, 10, 20, 30), head.getRGB(4, 4))
    }

    @Test
    fun `尺寸非法时返回 null`() {
        assertNull(MinecraftSkinCropper.cropHead(blankSkin(32, 32)))
        assertNull(MinecraftSkinCropper.cropHead(blankSkin(64, 128)))
        assertNull(MinecraftSkinCropper.cropHead(blankSkin(100, 100)))
    }

    @Test
    fun `面部全透明时返回 null 以便调用方进入下一级`() {
        assertNull(MinecraftSkinCropper.cropHead(blankSkin(64, 64)))
    }

    @Test
    fun `PNG 字节入口可解码并裁切`() {
        val skin = blankSkin(64, 64)
        fill(skin, 8, 8, 8, rgba(255, 1, 2, 3))

        val headPng = assertNotNull(MinecraftSkinCropper.cropHead(toPng(skin), 64))
        val decoded = ImageIO.read(ByteArrayInputStream(headPng))
        assertEquals(64, decoded.width)
        assertEquals(rgba(255, 1, 2, 3), decoded.getRGB(32, 32))
    }

    @Test
    fun `非图片字节返回 null 而不是抛异常`() {
        assertNull(MinecraftSkinCropper.cropHead(byteArrayOf(1, 2, 3, 4)))
    }
}
