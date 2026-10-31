// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.engine.TextToImg

/**
 * [TextToImg] 的边界与颜色契约测试。
 *
 * 守住两类历史缺陷：颜色映射（大小写归一、`§f` 为白而非黑），
 * 以及行尾孤立色码导致的越界崩溃。
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
        // 旧实现在此处读取 nowLine[j + 1] 抛 StringIndexOutOfBoundsException
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

    private fun metrics(): FontMetrics {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_BGR)
        val g = image.createGraphics()
        return try {
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 32)
            g.fontMetrics
        } finally {
            g.dispose()
        }
    }

    private fun lineCount(height: Int): Int = (height - 7) / (34 + 8)

    private fun renderWithMaxWidth(text: String, maxWidth: Int): ByteArray {
        val original = TextToImg.style
        return try {
            TextToImg.style = original.copy(maxLineWidth = maxWidth)
            render(text)
        } finally {
            TextToImg.style = original
        }
    }

    private fun assertNoBrokenSurrogates(lines: List<List<TextToImg.Segment>>) {
        for (line in lines) {
            val text = line.joinToString("") { it.text }
            var i = 0
            while (i < text.length) {
                val ch = text[i]
                if (ch.isHighSurrogate()) {
                    assertTrue(i + 1 < text.length && text[i + 1].isLowSurrogate(), "代理对不得跨行/跨段拆分")
                    i += 2
                } else {
                    assertFalse(ch.isLowSurrogate(), "不能出现孤立低代理")
                    i++
                }
            }
        }
    }

    @Test
    fun `长英文无空格按像素宽度硬断`() {
        val wrapped = renderWithMaxWidth("x".repeat(300), 120)
        if (wrapped.isEmpty()) return
        val image = png(wrapped)
        // HORIZONTAL_PADDING=32；总宽被限制为 maxLineWidth+padding 以内
        assertTrue(image.width <= 120 + 32, "长英文图片宽度不应超过 max_line_width+padding")
        assertTrue(lineCount(image.height) > 1, "无空格长英文必须换行")

        val unwrapped = renderWithMaxWidth("x".repeat(300), 0)
        val full = png(unwrapped)
        assertTrue(full.width > image.width, "max_line_width=0 时应保持单行旧行为")
        assertEquals(1, lineCount(full.height))
    }

    @Test
    fun `长中文按 CJK 边界换行`() {
        val text = "这是一段很长的中文文本用于测试自动换行".repeat(20)
        val bytes = renderWithMaxWidth(text, 120)
        if (bytes.isEmpty()) return
        val image = png(bytes)
        assertTrue(image.width <= 120 + 32, "长中文图片宽度不应超过 max_line_width+padding")
        assertTrue(lineCount(image.height) > 1, "长中文必须换行")
    }

    @Test
    fun `换行保留颜色段`() {
        val green = Color(0x55FF55)
        val original = "x".repeat(100)
        val lines = TextToImg.wrapSegments(
            listOf(listOf(TextToImg.Segment(original, green))),
            metrics(),
            100
        )
        assertEquals(original, lines.joinToString("") { line -> line.joinToString("") { it.text } })
        assertTrue(lines.size > 1, "100 个字符应被拆成多行")
        assertTrue(lines.all { line -> line.all { it.color == green } }, "换行后每段颜色必须保持")
    }

    @Test
    fun `emoji 代理对不得被拆行`() {
        val original = "😀".repeat(80)
        val lines = TextToImg.wrapSegments(
            listOf(listOf(TextToImg.Segment(original, Color.WHITE))),
            metrics(),
            120
        )
        assertEquals(original, lines.joinToString("") { line -> line.joinToString("") { it.text } })
        assertTrue(lines.size > 1, "长 emoji 行应换行")
        assertNoBrokenSurrogates(lines)
    }

    @Test
    fun `组合字符不得被拆行`() {
        val original = "e\u0301".repeat(80)
        val lines = TextToImg.wrapSegments(
            listOf(listOf(TextToImg.Segment(original, Color.WHITE))),
            metrics(),
            80
        )
        assertEquals(original, lines.joinToString("") { line -> line.joinToString("") { it.text } })
        assertTrue(lines.size > 1, "长组合字符文本应换行")
        for (line in lines) {
            val text = line.joinToString("") { it.text }
            assertFalse(text.startsWith("\u0301"), "组合重音不能出现在行首")
        }
    }

    @Test
    fun `原有换行符继续保留并参与高度`() {
        val bytes = renderWithMaxWidth("第一行\n第二行", 120)
        if (bytes.isEmpty()) return
        val image = png(bytes)
        assertEquals(2, lineCount(image.height), "原有 \\n 应保留为两行")
        assertTrue(image.width <= 120 + 32)
    }

    @Test
    fun `max_line_width 为零时关闭换行`() {
        val bytes = renderWithMaxWidth("中文".repeat(100), 0)
        if (bytes.isEmpty()) return
        val image = png(bytes)
        assertEquals(1, lineCount(image.height), "max_line_width=0 必须保持单行")
        assertTrue(image.width > 120 + 32, "关闭限制后宽度应由实际文本决定")
    }
}
