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
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [TextToImg] 的边界与颜色契约测试。
 *
 * 覆盖批次 1 的两个 P0 缺陷：
 * - **P0-3**：颜色映射（大小写归一、`§f` 为白而非黑）；
 * - **P0-4**：行尾孤立色码导致的越界崩溃。
 */
class TextToImgTest {

    private fun png(bytes: ByteArray): BufferedImage {
        assertTrue(bytes.size > 8, "输出不应为空")
        // 完整 PNG 魔数校验
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals(0x50.toByte(), bytes[1])
        assertEquals(0x4E.toByte(), bytes[2])
        assertEquals(0x47.toByte(), bytes[3])
        return assertNotNull(ImageIO.read(ByteArrayInputStream(bytes)), "输出应为可解码的 PNG")
    }

    private fun render(text: String, ampersand: Boolean = false): ByteArray {
        // 无图形环境下跳过（探针已给出明确结论）
        if (!TextToImg.isSupported) return ByteArray(0)
        return TextToImg.toByteArray(text, ampersand)
    }

    @Test
    fun `行尾孤立色码不再越界崩溃`() {
        // P0-4：旧实现在此处读取 nowLine[j + 1] 抛 StringIndexOutOfBoundsException
        val bytes = render("这是一行以色码结尾的文本§")
        if (bytes.isEmpty()) return
        png(bytes)
    }

    @Test
    fun `孤立色码前缀的半角变体同样安全`() {
        val bytes = render("尾部半角前缀ﾧ")
        if (bytes.isEmpty()) return
        png(bytes)
    }

    @Test
    fun `仅含色码与空文本均可出图`() {
        for (text in listOf("", "\n", "\n\n", "§a§b§c", "§r", "§k§l§m§n§o")) {
            val bytes = render(text)
            if (bytes.isEmpty()) return
            png(bytes)
        }
    }

    @Test
    fun `颜色码大小写等价`() {
        val lower = render("§a绿色文字")
        if (lower.isEmpty()) return
        val upper = render("§A绿色文字")
        assertContentEquals(lower, upper, "§A 与 §a 应产出完全一致的图片（大小写归一）")
    }

    @Test
    fun `白色与黑色为不同颜色且大小写等价`() {
        val black = render("§0文字")
        if (black.isEmpty()) return
        val white = render("§f文字")
        val whiteUpper = render("§F文字")

        assertContentEquals(white, whiteUpper, "§F 与 §f 应等价")
        assertTrue(
            !black.contentEquals(white),
            "§0（黑）与 §f（白）必须是不同颜色 —— 旧实现把 §f 也映射成黑色"
        )
    }

    @Test
    fun `默认不把和号解释为色码`() {
        val plain = render("R&D")
        if (plain.isEmpty()) return
        val coded = render("R&D", ampersand = true)
        assertTrue(
            !plain.contentEquals(coded),
            "ampersandCodes=false 时 &D 应作为正文，true 时才作为色码"
        )
    }

    @Test
    fun `自动描边不改变画布尺寸`() {
        val original = TextToImg.style
        try {
            val text = "§0暗色§f亮色§8灰字"
            TextToImg.style = original.copy(autoOutline = false)
            val withoutOutline = render(text)
            if (withoutOutline.isEmpty()) return
            TextToImg.style = original.copy(autoOutline = true)
            val withOutline = render(text)

            val a = png(withoutOutline)
            val b = png(withOutline)
            assertEquals(a.width, b.width, "描边不应改变布局宽度")
            assertEquals(a.height, b.height, "描边不应改变布局高度")
            assertTrue(
                !withoutOutline.contentEquals(withOutline),
                "启用自动描边后像素应发生变化"
            )
        } finally {
            TextToImg.style = original
        }
    }

    @Test
    fun `超长单行与多行均可出图`() {
        val long = render("x".repeat(5000))
        if (long.isEmpty()) return
        png(long)

        val multiline = render((1..200).joinToString("\n") { "第 $it 行 §a彩色" })
        png(multiline)
    }

    @Test
    fun `极端色码密度不抛异常`() {
        val dense = buildString {
            repeat(2000) { append("§").append("0123456789abcdefg"[it % 17]) }
        }
        val bytes = render(dense)
        if (bytes.isEmpty()) return
        png(bytes)
    }
}
