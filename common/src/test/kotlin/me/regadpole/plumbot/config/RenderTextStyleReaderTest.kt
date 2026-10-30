// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.render.RenderTextStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `render.text` → [RenderTextStyle] 的贯通测试。
 *
 * 回归点：`max_line_width` 若只存在于门户默认值、启动路径从不读取，改配置毫无效果而全部
 * 测试仍然通过。
 */
class RenderTextStyleReaderTest {

    private fun read(vararg lines: String): RenderTextStyle {
        val file = Files.createTempFile("plumbot-render-text", ".yml")
        try {
            file.writeText(lines.joinToString("\n"))
            val node = YamlConfigurationLoader.builder().file(file.toFile()).build().load()
            return RenderTextStyleReader.read(YamlConfigurator(node))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun readsConfiguredMaxLineWidth() {
        val style = read("render:", "  text:", "    max_line_width: 480")
        assertEquals(480, style.maxLineWidth)
    }

    @Test
    fun zeroDisablesWrapping() {
        val style = read("render:", "  text:", "    max_line_width: 0")
        assertEquals(0, style.maxLineWidth)
    }

    @Test
    fun negativeClampsToZero() {
        val style = read("render:", "  text:", "    max_line_width: -3")
        assertEquals(0, style.maxLineWidth)
    }

    @Test
    fun missingMaxLineWidthFallsBackToDefault() {
        val style = read("render:", "  text:", "    auto_outline: true")
        assertEquals(RenderTextStyle().maxLineWidth, style.maxLineWidth)
        assertEquals(960, style.maxLineWidth)
    }

    @Test
    fun nonNumericMaxLineWidthFallsBackToDefault() {
        // configurate 的 node.int 会把 "abc" 读成 0；直接采信就会变成"用户关闭换行"。
        val style = read("render:", "  text:", "    max_line_width: abc")
        assertEquals(960, style.maxLineWidth)
    }

    @Test
    fun readsColorsAndOutline() {
        val style = read(
            "render:",
            "  text:",
            "    base: \"#112233\"",
            "    color: \"#445566\"",
            "    auto_outline: false",
            "    contrast_threshold: 3.0"
        )
        assertEquals("#112233", style.baseColor)
        assertEquals("#445566", style.textColor)
        assertEquals(false, style.autoOutline)
        assertEquals(3.0, style.contrastThreshold, 0.0001)
    }

    @Test
    fun blankOrMissingColorsFallBack() {
        val style = read("render:", "  text:", "    base: ''")
        val defaults = RenderTextStyle()
        assertEquals(defaults.baseColor, style.baseColor)
        assertEquals(defaults.textColor, style.textColor)
        assertEquals(defaults.contrastThreshold, style.contrastThreshold, 0.0001)
    }

    @Test
    fun invalidThresholdFallsBack() {
        val style = read("render:", "  text:", "    contrast_threshold: 0")
        assertEquals(RenderTextStyle().contrastThreshold, style.contrastThreshold, 0.0001)
    }
}
