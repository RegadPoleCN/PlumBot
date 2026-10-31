// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.theme.RenderFontInjector

/**
 * 页面字体注入测试（中文 / Emoji 豆腐块）：注入服主已为 AWT 配置的同一个字体，
 * 而不是内嵌模板字体 —— 后者会让单文件模板从几十 KB 涨到数 MB。
 */
class RenderFontInjectorTest {

    private fun tempFont(name: String, bytes: ByteArray): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "plumbot-font-${System.nanoTime()}")
        dir.mkdirs()
        return File(dir, name).apply { writeBytes(bytes) }
    }

    @Test
    fun `生成包含 data URL 与字体族的 CSS`() {
        val font = tempFont("MiSans-Normal.ttf", ByteArray(64) { it.toByte() })

        val outcome = assertIs<RenderFontInjector.Outcome.Css>(
            RenderFontInjector.buildCss(font, maxBytes = 1024)
        )
        assertTrue(outcome.text.contains("@font-face"), "应生成 @font-face")
        assertTrue(outcome.text.contains("data:font/ttf;base64,"), "应以 data URL 内嵌")
        assertTrue(outcome.text.contains(RenderFontInjector.FONT_FAMILY), "应声明字体族名")
        assertTrue(outcome.text.contains("format('truetype')"), "TTF 应声明 truetype")
        assertTrue(outcome.text.contains("!important"), "必须覆盖模板自身的字体栈")
    }

    @Test
    fun `未配置字体时跳过并给出原因`() {
        val outcome = assertIs<RenderFontInjector.Outcome.Skipped>(
            RenderFontInjector.buildCss(null, maxBytes = 1024)
        )
        assertTrue(outcome.reason.contains("render.font"))
    }

    @Test
    fun `文件不存在时跳过`() {
        val outcome = assertIs<RenderFontInjector.Outcome.Skipped>(
            RenderFontInjector.buildCss(File("/definitely/not/here.ttf"), maxBytes = 1024)
        )
        assertTrue(outcome.reason.contains("不存在"))
    }

    @Test
    fun `超过体积上限时跳过而不是把超大字体塞进每一页`() {
        val font = tempFont("big.ttf", ByteArray(4096) { 1 })
        val outcome = assertIs<RenderFontInjector.Outcome.Skipped>(
            RenderFontInjector.buildCss(font, maxBytes = 1024)
        )
        assertTrue(outcome.reason.contains("超过上限"), outcome.reason)
    }

    @Test
    fun `上限为 0 表示不限制`() {
        val font = tempFont("x.ttf", ByteArray(4096) { 1 })
        assertIs<RenderFontInjector.Outcome.Css>(
            RenderFontInjector.buildCss(font, maxBytes = 0)
        )
    }

    @Test
    fun `空文件被跳过`() {
        val font = tempFont("empty.ttf", ByteArray(0))
        assertIs<RenderFontInjector.Outcome.Skipped>(
            RenderFontInjector.buildCss(font, maxBytes = 1024)
        )
    }

    @Test
    fun `按扩展名推断字体格式`() {
        fun cssFor(name: String): String {
            val font = tempFont(name, ByteArray(32) { 2 })
            return assertIs<RenderFontInjector.Outcome.Css>(
                RenderFontInjector.buildCss(font, maxBytes = 1024)
            ).text
        }

        assertTrue(cssFor("a.otf").contains("data:font/otf;base64,"))
        assertTrue(cssFor("a.otf").contains("format('opentype')"))
        assertTrue(cssFor("a.woff2").contains("data:font/woff2;base64,"))
        assertTrue(cssFor("a.woff").contains("data:font/woff;base64,"))
        assertTrue(cssFor("a.ttf").contains("data:font/ttf;base64,"))
    }

    @Test
    fun `注入的字节可被还原（base64 无损）`() {
        val original = ByteArray(128) { (it * 7).toByte() }
        val font = tempFont("f.ttf", original)
        val css = assertIs<RenderFontInjector.Outcome.Css>(
            RenderFontInjector.buildCss(font, maxBytes = 4096)
        ).text

        val base64 = css.substringAfter("base64,").substringBefore("')")
        assertEquals(original.toList(), java.util.Base64.getDecoder().decode(base64).toList())
    }
}
