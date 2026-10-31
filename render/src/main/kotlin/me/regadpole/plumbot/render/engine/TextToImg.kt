// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine

import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import me.regadpole.plumbot.render.RenderPortal

/**
 * AWT 文本转图片引擎（`engine=awt` 的内部驱动实现）。
 *
 * 渲染规则与前端模板一致（由 `render-protocol.json` 的 `text` 节点描述）：带颜色的底板；
 * 逐段按 WCAG 对比度决定是否**自动描边**（描边色取底板的亮度反差色），保证 `§0` 黑与
 * `§f` 白等 16 色都可读；色码大小写均可命中，行尾孤立色码安全跳过。
 */
internal object TextToImg {

    private const val DEFAULT_FONT_SIZE = 32f

    /** 单行文本默认最大像素宽度；`0` 表示不限制（旧行为）。 */
    private const val DEFAULT_MAX_LINE_WIDTH = 960
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

    /** 文本渲染外观；由门户按协议清单与 config.yml 注入。 */
    data class Style(
        val plateColor: Color = Color(0x1E2430),
        val textColor: Color = Color(0xE6E6E6),
        val autoOutline: Boolean = true,
        val contrastThreshold: Double = 4.5,
        val maxLineWidth: Int = DEFAULT_MAX_LINE_WIDTH
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
     * 必须**真实走一遍字体度量与字形绘制**：只构造 1x1 BufferedImage 在任何环境都会成功，
     * 发现不了缺 fontconfig/X11 时的真实失败点。结果被缓存，失败后交由上层回退纯文本。
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

    internal data class Segment(val text: String, val color: Color)

    /**
     * 按颜色码切分一行文本。
     *
     * 色码位于行尾时**直接结束解析**，不再读取 `line[i + 1]`（否则越界崩溃）。
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

        // 度量必须用与最终图像相同类型与渲染提示的探针 Graphics2D：换行与绘制共用同一把
        // FontMetrics，否则图片宽度和实际绘制宽度会不一致。
        val layout = withMetrics(currentFont) { metrics ->
            val wrapped = wrapSegments(parsedLines, metrics, currentStyle.maxLineWidth)
            val measured = measureMaxWidth(wrapped, metrics)
            val limited = if (currentStyle.maxLineWidth > 0) {
                min(measured, currentStyle.maxLineWidth)
            } else {
                measured
            }
            Layout(wrapped, max(1, limited + HORIZONTAL_PADDING))
        }

        val lineCount = layout.lines.size
        val totalHeight = max(1, lineCount * LINE_HEIGHT + (lineCount - 1) * LINE_SPACING + VERTICAL_PADDING)

        val image = BufferedImage(layout.width, totalHeight, IMAGE_TYPE)
        val g = image.createGraphics()
        try {
            applyHints(g)
            g.font = currentFont
            g.color = currentStyle.plateColor
            g.fillRect(0, 0, layout.width, totalHeight)
            drawLines(g, layout.lines, currentStyle)
        } finally {
            g.dispose()
        }

        val os = ByteArrayOutputStream()
        val mcios = MemoryCacheImageOutputStream(os)
        ImageIO.write(image, "png", mcios)
        mcios.close()
        return os.toByteArray()
    }

    private data class Layout(val lines: List<List<Segment>>, val width: Int)

    /** 在探针 Graphics2D 上获取与绘制环境一致的 FontMetrics，并保证销毁图形上下文。 */
    private fun <T> withMetrics(currentFont: Font, block: (FontMetrics) -> T): T {
        val probe = BufferedImage(1, 1, IMAGE_TYPE)
        val g = probe.createGraphics()
        return try {
            applyHints(g)
            g.font = currentFont
            block(g.fontMetrics)
        } finally {
            g.dispose()
        }
    }

    private data class TextCluster(val text: String, val color: Color)

    /**
     * 将逻辑行按像素宽度换行：优先在空格 / 连字符之后或 CJK 字符之前断开；没有断点时
     * 硬断，但不会拆开代理对、组合字符或 ZWJ emoji（由 [BreakIterator] 保证）；每个
     * [Segment] 的颜色随文本一起保留；[maxWidth] 为 0 或负数时原样返回。
     */
    internal fun wrapSegments(
        parsedLines: List<List<Segment>>,
        metrics: FontMetrics,
        maxWidth: Int
    ): List<List<Segment>> {
        if (maxWidth <= 0) return parsedLines

        val wrapped = ArrayList<List<Segment>>(parsedLines.size)
        for (segments in parsedLines) {
            val pieces = flattenSegments(segments)
            if (pieces.isEmpty()) {
                wrapped.add(emptyList())
                continue
            }

            var start = 0
            while (start < pieces.size) {
                var end = start
                var width = 0
                var lastBreak = -1

                while (end < pieces.size) {
                    val piece = pieces[end]
                    val pieceWidth = metrics.stringWidth(piece.text)
                    if (width + pieceWidth > maxWidth && end > start) break

                    // CJK 字符允许在它之前断行；记录的是下一行的起始下标。
                    if (isCjk(piece.text) && end > start) {
                        lastBreak = maxOf(lastBreak, end)
                    }
                    // 空格/连字符允许在它之后断行，断点包含该字符。
                    if (isBreakAfter(piece.text)) {
                        lastBreak = maxOf(lastBreak, end + 1)
                    }

                    width += pieceWidth
                    end++
                }

                val breakAt = if (end >= pieces.size) {
                    end
                } else if (lastBreak > start && lastBreak < end) {
                    lastBreak
                } else {
                    end
                }.coerceAtLeast(start + 1)

                wrapped.add(buildSegments(pieces.subList(start, breakAt)))
                start = breakAt
            }
        }
        return wrapped
    }

    private fun flattenSegments(segments: List<Segment>): List<TextCluster> {
        val out = ArrayList<TextCluster>()
        for (segment in segments) {
            for (cluster in graphemeClusters(segment.text)) {
                out.add(TextCluster(cluster, segment.color))
            }
        }
        return out
    }

    private fun buildSegments(pieces: List<TextCluster>): List<Segment> {
        if (pieces.isEmpty()) return emptyList()
        val out = ArrayList<Segment>(1)
        val text = StringBuilder()
        var currentColor: Color? = null

        for (piece in pieces) {
            val color = currentColor
            if (color == null) {
                currentColor = piece.color
            } else if (color != piece.color) {
                out.add(Segment(text.toString(), color))
                text.setLength(0)
                currentColor = piece.color
            }
            text.append(piece.text)
        }
        currentColor?.let { out.add(Segment(text.toString(), it)) }
        return out
    }

    /** 按 Unicode 扩展字素簇切分，避免把代理对、组合重音或 ZWJ emoji 拆到两行。 */
    private fun graphemeClusters(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val out = ArrayList<String>(text.length)
        var start = iterator.first()
        if (start == BreakIterator.DONE) return out
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            out.add(text.substring(start, end))
            start = end
            end = iterator.next()
        }
        return out
    }

    private fun isBreakAfter(cluster: String): Boolean {
        if (cluster.isEmpty()) return false
        val first = cluster.codePointAt(0)
        if (Character.isWhitespace(first)) return true
        return first == '-'.code || first == 0x2010 || first == 0x2011 ||
            first == 0x2012 || first == 0x2013 || first == 0x2014 || first == 0x2212
    }

    /** 是否 CJK 字符；用于在字符边界断行（无需依赖空格）。 */
    private fun isCjk(cluster: String): Boolean {
        if (cluster.isEmpty()) return false
        val cp = cluster.codePointAt(0)
        return cp in 0x1100..0x11FF ||
            cp in 0x2E80..0x2FFF ||
            cp in 0x3000..0x303F ||
            cp in 0x3040..0x30FF ||
            cp in 0x3400..0x4DBF ||
            cp in 0x4E00..0x9FFF ||
            cp in 0xAC00..0xD7AF ||
            cp in 0xF900..0xFAFF ||
            cp in 0xFE30..0xFE4F ||
            cp in 0xFF00..0xFFEF ||
            cp in 0x20000..0x2FA1F
    }

    private fun measureMaxWidth(parsedLines: List<List<Segment>>, metrics: FontMetrics): Int {
        var widest = 0
        for (segments in parsedLines) {
            var width = 0
            for (segment in segments) {
                for (cluster in graphemeClusters(segment.text)) {
                    width += metrics.stringWidth(cluster)
                }
            }
            if (width > widest) widest = width
        }
        return widest
    }

    private fun drawLines(g: Graphics2D, parsedLines: List<List<Segment>>, currentStyle: Style) {
        val metrics: FontMetrics = g.fontMetrics
        val outlineColor = if (relativeLuminance(currentStyle.plateColor) > 0.5) Color.BLACK else Color.WHITE
        val outlineRadius = if (DEFAULT_FONT_SIZE >= 48f) 2 else 1

        for (lineIndex in parsedLines.indices) {
            var x = TEXT_START_X
            val baseline = FIRST_LINE_BASELINE + lineIndex * (LINE_HEIGHT + LINE_SPACING)
            for (segment in parsedLines[lineIndex]) {
                val needsOutline = currentStyle.autoOutline &&
                    contrastRatio(segment.color, currentStyle.plateColor) < currentStyle.contrastThreshold
                for (cluster in graphemeClusters(segment.text)) {
                    val advance = metrics.stringWidth(cluster)
                    if (needsOutline) {
                        g.color = outlineColor
                        for (dx in -outlineRadius..outlineRadius) {
                            for (dy in -outlineRadius..outlineRadius) {
                                if (dx == 0 && dy == 0) continue
                                g.drawString(cluster, x + dx, baseline + dy)
                            }
                        }
                    }
                    g.color = segment.color
                    g.drawString(cluster, x, baseline)
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
     * @param string 要渲染的正文，可含 `§` 色码
     * @param ampersandCodes 是否把 `&` 也视作色码前缀。默认关闭，避免把 "R&D" 这类正文误判为色码；
     *   控制台回显（`&`-legacy）路径应显式传 `true`。
     */
    fun toByteArray(string: String, ampersandCodes: Boolean = false): ByteArray {
        return render(string, ampersandCodes)
    }

    /** 渲染文本为临时 PNG 文件。 */
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

