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

package me.regadpole.plumbot.utils

import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream


object TextToImg {

    private const val DEFAULT_FONT_SIZE = 32f
    private const val LINE_HEIGHT = 34
    private const val LINE_SPACING = 8
    private const val VERTICAL_PADDING = 15
    private const val HORIZONTAL_PADDING = 32
    private const val TEXT_START_X = 16
    private const val FIRST_LINE_BASELINE = 34

    private val DEFAULT_BACKGROUND_COLOR = Color("FFFFFF".toInt(16))
    private val DEFAULT_TEXT_COLOR = Color.black

    private val COLOR_MAP: Map<Char, Color> = mapOf(
        '0' to Color.black,
        '1' to Color("0000AA".toInt(16)),
        '2' to Color("00AA00".toInt(16)),
        '3' to Color("00AAAA".toInt(16)),
        '4' to Color("AA0000".toInt(16)),
        '5' to Color("AA00AA".toInt(16)),
        '6' to Color("FFAA00".toInt(16)),
        '7' to Color("AAAAAA".toInt(16)),
        '8' to Color("555555".toInt(16)),
        '9' to Color("5555FF".toInt(16)),
        'a' to Color("55FF55".toInt(16)),
        'b' to Color("55FFFF".toInt(16)),
        'c' to Color("FF5555".toInt(16)),
        'd' to Color("FF55FF".toInt(16)),
        'e' to Color("FFFF55".toInt(16)),
        'f' to Color.black,
        'g' to Color("DDD605".toInt(16))
    )

    private var font: Font? = null
    private var fm: FontMetrics? = null
    var ttfFile: File? = null
        set(value) {
            field = value
            font = null
            fm = null
        }

    /**
     * 系统图形渲染环境探针。
     * 首次调用时尝试检测 AWT 图形环境；若本地缺失 X11/freetype 等本地库，自动标记为不可用，
     * 避免后续反复尝试绘制导致抛出致命 Error。
     */
    val isSupported: Boolean by lazy {
        try {
            GraphicsEnvironment.isHeadless()
            val img = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
            val g = img.graphics
            g.dispose()
            true
        } catch (t: Throwable) {
            System.err.println("[PlumBot] 系统环境缺少 AWT 图形环境支持: ${t.message}，图片渲染功能已停用，将全自动回退为纯文本模式。")
            false
        }
    }

    fun reset() {
        font = null
        fm = null
    }

    private fun ensureFontInitialized() {
        if (font != null && fm != null) return

        val currentTtfFile = ttfFile
        if (currentTtfFile != null && currentTtfFile.exists() && currentTtfFile.isFile) {
            try {
                val createdFont = Font.createFont(Font.TRUETYPE_FONT, currentTtfFile.toURI().toURL().openStream())
                val derivedFont = createdFont.deriveFont(DEFAULT_FONT_SIZE)
                val derivedFm = Canvas().getFontMetrics(derivedFont)
                font = derivedFont
                fm = derivedFm
                return
            } catch (e: Exception) {
                System.err.println("[PlumBot] 读取字体文件失败 (${currentTtfFile.absolutePath}): ${e.message}，回退使用系统默认无衬线字体。")
            }
        }
        val defaultFont = Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE.toInt())
        font = defaultFont
        fm = Canvas().getFontMetrics(defaultFont)
    }

    @Throws(IOException::class)
    private fun toImg(text: String): ByteArray {
        if (!isSupported) {
            throw UnsupportedOperationException("当前环境缺少 AWT 图形库支持，无法生成图片。")
        }
        ensureFontInitialized()

        val currentFont = font ?: Font(Font.SANS_SERIF, Font.PLAIN, DEFAULT_FONT_SIZE.toInt())
        val currentFm = fm ?: Canvas().getFontMetrics(currentFont)

        val strings = text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var minX = 0
        for (line in strings) {
            var lines = line
            lines = lines.replace("ﾧ\\S".toRegex(), "")
            lines = lines.replace("§\\S".toRegex(), "")

            val result = currentFm.stringWidth(lines)

            if (minX < result) minX = result
        }
        val totalHeight = strings.size * LINE_HEIGHT + (strings.size - 1) * LINE_SPACING + VERTICAL_PADDING
        minX += HORIZONTAL_PADDING
        val image = BufferedImage(
            minX, totalHeight,
            BufferedImage.TYPE_INT_BGR
        )
        val g = image.graphics
        g.setClip(0, 0, minX, totalHeight)
        g.color = DEFAULT_BACKGROUND_COLOR
        g.fillRect(0, 0, minX, totalHeight)
        g.color = DEFAULT_TEXT_COLOR
        g.font = currentFont
        for (i in strings.indices) {
            val nowLine = strings[i]
            var dex = 0
            var nowX = TEXT_START_X

            var j = 0
            while (j < nowLine.length) {
                if (nowLine[j] == 'ﾧ' || nowLine[j] == '§') {
                    g.color = COLOR_MAP[nowLine[j + 1]] ?: DEFAULT_TEXT_COLOR
                    j++
                    dex += 2
                } else {
                    g.drawString(nowLine[dex].toString(), nowX, FIRST_LINE_BASELINE + i * (LINE_HEIGHT + LINE_SPACING))
                    nowX += currentFm.charWidth(nowLine[dex])
                    dex++
                }
                j++
            }
        }
        g.dispose()
        val os = ByteArrayOutputStream()

        val mcios = MemoryCacheImageOutputStream(os)
        ImageIO.write(image, "png", mcios)
        mcios.close()
        return os.toByteArray()
    }

    /**
     * 渲染文本为 PNG 图片字节数组（协议中立，不带任何 CQ 码或协议封装）。
     */
    fun toByteArray(string: String): ByteArray {
        return toImg(string)
    }

    /**
     * 渲染文本为临时 PNG 文件。
     */
    fun toFile(string: String): File {
        try {
            val bytes = toImg(string)
            val file = File.createTempFile("PlumBot", ".png")
            file.writeBytes(bytes)
            return file
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}
