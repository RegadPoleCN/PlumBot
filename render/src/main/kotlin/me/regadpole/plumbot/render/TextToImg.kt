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
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * AWT 文本转图片引擎（`engine=awt` 的内部驱动实现）。
 *
 * 渲染规则（与前端模板保持一致，规则由 `render-protocol.json` 的 `text` 节点统一描述）：
 * 1. **带颜色的底板**取代原纯白底板；
 * 2. 逐段计算字色与底板的 WCAG 对比度，低于阈值时**自动描边**（描边色取底板的亮度反差色），
 *    从而保证包含 `§0` 黑与 `§f` 白在内的全部 16 色都可读；
 * 3. 颜色码大小写均可命中；行尾孤立色码安全跳过（不越界）。
 */
internal object TextToImg {

    private const val DEFAULT_FONT_SIZE = 32f
    private const val LINE_HEIGHT = 34
    private const val LINE_SPACING = 8
    private const val VERTICAL_PADDING = 15
    private const val HORIZONTAL_PADDING = 32
    private const val TEXT_START_X = 16
    private const val FIRST_LINE_BASELINE = 34

    /** 与绘制图像保持一致的图像类型，避免度量与绘制使用不同的 FontRenderContext。 */
    private const val IMAGE_TYPE = BufferedImage.TYPE_INT_BGR

    /** 兼容历史遗留的半角色码前缀。 */
    private const val LEGACY_PREFIX = 'ﾧ'

    /**
     * 文本渲染外观。
     *
     * @param plateColor 底板色（带颜色的底板）
     * @param textColor 无颜色码时的默认字色
     * @param autoOutline 对比度不足时自动描边
     * @param contrastThreshold WCAG 对比度阈值，低于该值触发描边
     */
    data class Style(
        val plateColor: Color = Color(0x1E2430),
        val textColor: Color = Color(0xE6E6E6),
        val autoOutline: Boolean = true,
        val contrastThreshold: Double = 4.5
    ) {
        companion object {
            val DEFAULT = Style()
        }
    }

    /** 官方 16 色 + 基岩版 minecoin_gold；键统一为小写，查表前对色码做小写归一。 */
    private val COLOR_MAP: Map<Char, Color> = mapOf(
        '0' to Color(0x000000),
        '1' to Color(0x0000AA),
        '2' to Color(0x00AA00),
        '3' to Color(0x00AAAA),
        '4' to Color(0xAA0000),
        '5' to Color(0xAA00AA),
        '6' to Color(0xFFAA00),
        '7' to Color(0xAAAAAA),
        '8' to Color(0x555555),
        '9' to Color(0x5555FF),
        'a' to Color(0x55FF55),
        'b' to Color(0x55FFFF),
        'c' to Color(0xFF5555),
        'd' to Color(0xFF55FF),
        'e' to Color(0xFFFF55),
        'f' to Color(0xFFFFFF),
        'g' to Color(0xDDD605)
    )

    /** 外观配置；由门户（RenderPortal）统一注入。 */
    @Volatile
    var style: Style = Style.DEFAULT

    /** 规范日志出口；由 AwtImageRenderer 注入，避免绕过平台日志体系。 */
    @Volatile
    var logSink: ((String) -> Unit)? = null

    @Volatile
    private var font: Font? = null

    @Volatile
    var ttfFile: File? = null
        set(value) {
            field = value
            font = null
        }

    /**
     * 系统图形渲染环境探针。
     *
     * 与旧实现的关键差别：**真实走一遍字体度量与字形绘制**（旧实现只构造 1x1 BufferedImage，
     * 而这在任何环境都会成功，无法发现缺 fontconfig/X11 时的真实失败点）。
     * 首次调用结果被缓存，失败后不再反复尝试，交由上层全自动回退纯文本。
     */
    val isSupported: Boolean by lazy {
        try {
            val probeFont = resolveFont()
            val probe = BufferedImage(1, 1, IMAGE_TYPE)
            val g = probe.createGraphics()
            try {
                applyHints(g)
                g.font = probeFont
                val metrics = g.fontMetrics
                metrics.stringWidth("A")
                g.drawString("A", 0, 0)
            } finally {
                g.dispose()
            }
            true
        } catch (t: Throwable) {
            log("[PlumBot] 系统环境缺少 AWT 图形环境支持: ${t.message}，图片渲染功能已停用，将全自动回退为纯文本模式。")
            false
        }
    }

    fun reset() {
        font = null
    }

    private fun log(message: String) {
        val sink = logSink
        if (sink != null) sink(message) else System.err.println(message)
    }

    private fun applyHints(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF)
    }

    private fun resolveFont(): Font {
        font?.let { return it }
        synchronized(this) {
            font?.let { return it }
            val currentTtf = ttfFile
            val resolved = if (currentTtf != null && currentTtf.exists() && currentTtf.isFile) {
                try {
                    currentTtf.toURI().toURL().openStream().use { input ->
                        Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(DEFAULT_FONT_SIZE)
                    }
                } catch (e: Exception) {
                    log("[PlumBot] 读取字体文件失败 (${currentTtf.absolutePath}): ${e.message}，回退使用系统默认无衬线字体。")
                    Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE.toInt())
                }
            } else {
                Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE.toInt())
            }
            font = resolved
            return resolved
        }
    }

    private data class Segment(val text: String, val color: Color)

    /**
     * 按颜色码切分一行文本。
     *
     * 安全要点：色码位于行尾时**直接结束解析**，不再读取 `line[i + 1]`（修复越界崩溃）。
     * 非颜色格式码（`§k/l/m/n/o`）不绘制也不改色；`§r` 恢复默认字色。
     */
    private fun parseSegments(line: String, ampersandCodes: Boolean, baseColor: Color): List<Segment> {
        if (line.isEmpty()) return emptyList()
        val out = ArrayList<Segment>(1)
        val sb = StringBuilder()
        var current = baseColor
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val isPrefix = c == '\u00A7' || c == LEGACY_PREFIX || (ampersandCodes && c == '&')
            if (!isPrefix) {
                sb.append(c)
                i++
                continue
            }
            // 行尾孤立色码：安全终止
            if (i + 1 >= line.length) break
            val code = line[i + 1].lowercaseChar()
            val mapped = COLOR_MAP[code]
            if (mapped != null) {
                if (sb.isNotEmpty()) {
                    out.add(Segment(sb.toString(), current))
                    sb.setLength(0)
                }
                current = mapped
            } else if (code == 'r') {
                if (sb.isNotEmpty()) {
                    out.add(Segment(sb.toString(), current))
                    sb.setLength(0)
                }
                current = baseColor
            }
            i += 2
        }
        if (sb.isNotEmpty()) out.add(Segment(sb.toString(), current))
        return out
    }

    private fun render(text: String, ampersandCodes: Boolean): ByteArray {
        if (!isSupported) {
            throw UnsupportedOperationException("当前环境缺少 AWT 图形库支持，无法生成图片。")
        }
        val currentFont = resolveFont()
        val currentStyle = style

        val lines = text.split('\n').dropLastWhile { it.isEmpty() }
        val parsedLines = lines.map { parseSegments(it, ampersandCodes, currentStyle.textColor) }

        // 度量：使用与最终图像完全相同类型与渲染提示的探针 Graphics2D 获取 FontMetrics
        val maxTextWidth = measureMaxWidth(parsedLines, currentFont)

        val totalWidth = max(1, maxTextWidth + HORIZONTAL_PADDING)
        val lineCount = parsedLines.size
        val totalHeight = max(1, lineCount * LINE_HEIGHT + (lineCount - 1) * LINE_SPACING + VERTICAL_PADDING)

        val image = BufferedImage(totalWidth, totalHeight, IMAGE_TYPE)
        val g = image.createGraphics()
        try {
            applyHints(g)
            g.font = currentFont
            g.color = currentStyle.plateColor
            g.fillRect(0, 0, totalWidth, totalHeight)
            drawLines(g, parsedLines, currentStyle)
        } finally {
            g.dispose()
        }

        val os = ByteArrayOutputStream()
        val mcios = MemoryCacheImageOutputStream(os)
        ImageIO.write(image, "png", mcios)
        mcios.close()
        return os.toByteArray()
    }

    private fun measureMaxWidth(parsedLines: List<List<Segment>>, currentFont: Font): Int {
        val probe = BufferedImage(1, 1, IMAGE_TYPE)
        val g = probe.createGraphics()
        return try {
            applyHints(g)
            g.font = currentFont
            val metrics = g.fontMetrics
            var widest = 0
            for (segments in parsedLines) {
                var width = 0
                for (segment in segments) {
                    for (ch in segment.text) {
                        width += metrics.charWidth(ch)
                    }
                }
                if (width > widest) widest = width
            }
            widest
        } finally {
            g.dispose()
        }
    }

    private fun drawLines(g: Graphics2D, parsedLines: List<List<Segment>>, currentStyle: Style) {
        val metrics: FontMetrics = g.fontMetrics
        val outlineColor = if (relativeLuminance(currentStyle.plateColor) > 0.5) Color.BLACK else Color.WHITE
        val outlineRadius = if (DEFAULT_FONT_SIZE >= 48f) 2 else 1
        val single = CharArray(1)

        for (lineIndex in parsedLines.indices) {
            var x = TEXT_START_X
            val baseline = FIRST_LINE_BASELINE + lineIndex * (LINE_HEIGHT + LINE_SPACING)
            for (segment in parsedLines[lineIndex]) {
                val needsOutline = currentStyle.autoOutline &&
                    contrastRatio(segment.color, currentStyle.plateColor) < currentStyle.contrastThreshold
                for (ch in segment.text) {
                    val advance = metrics.charWidth(ch)
                    single[0] = ch
                    if (needsOutline) {
                        g.color = outlineColor
                        for (dx in -outlineRadius..outlineRadius) {
                            for (dy in -outlineRadius..outlineRadius) {
                                if (dx == 0 && dy == 0) continue
                                g.drawChars(single, 0, 1, x + dx, baseline + dy)
                            }
                        }
                    }
                    g.color = segment.color
                    g.drawChars(single, 0, 1, x, baseline)
                    x += advance
                }
            }
        }
    }

    private fun channelLuminance(value: Int): Double {
        val s = value / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * channelLuminance(color.red) +
            0.7152 * channelLuminance(color.green) +
            0.0722 * channelLuminance(color.blue)

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /**
     * 渲染文本为 PNG 图片字节数组（协议中立，不带任何 CQ 码或协议封装）。
     *
     * @param ampersandCodes 是否把 `&` 也视作色码前缀。默认关闭，避免把 "R&D" 这类正文误判为色码；
     *   控制台回显（`&`-legacy）路径应显式传 `true`。
     */
    fun toByteArray(string: String, ampersandCodes: Boolean = false): ByteArray {
        return render(string, ampersandCodes)
    }

    /**
     * 渲染文本为临时 PNG 文件。
     */
    fun toFile(string: String, ampersandCodes: Boolean = false): File {
        try {
            val bytes = render(string, ampersandCodes)
            val file = File.createTempFile("PlumBot", ".png")
            file.writeBytes(bytes)
            return file
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
