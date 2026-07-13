package me.regadpole.plumbot.utils

import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.util.*
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

    @Throws(IOException::class)
    private fun toImg(text: String): ByteArray {
        val currentTtfFile = ttfFile ?: throw IllegalStateException("TextToImg 字体文件未配置，请先设置 ttfFile")
        if (fm == null) {
            try {
                val createdFont = Font.createFont(Font.TRUETYPE_FONT, currentTtfFile.toURI().toURL().openStream())
                font = createdFont.deriveFont(DEFAULT_FONT_SIZE)
                fm = Toolkit.getDefaultToolkit().getFontMetrics(font)
            } catch (e: FontFormatException) {
                throw IllegalStateException("TextToImg 字体文件格式不正确: ${currentTtfFile.absolutePath}", e)
            } catch (e: IOException) {
                throw IllegalStateException("TextToImg 无法读取字体文件: ${currentTtfFile.absolutePath}", e)
            }
        }

        val currentFont = font ?: throw IllegalStateException("TextToImg 字体未初始化")
        val currentFm = fm ?: throw IllegalStateException("TextToImg 字体度量未初始化")

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
     * 将inputstream转为Base64
     *
     * @param bytes
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun getBase64FromInputStream(bytes: ByteArray): String? {
        val data: ByteArray

        val inputStream: InputStream = ByteArrayInputStream(bytes)

        try {
            val swapStream = ByteArrayOutputStream()
            val buff = ByteArray(100)
            var rc: Int
            while ((inputStream.read(buff, 0, 100).also { rc = it }) > 0) {
                swapStream.write(buff, 0, rc)
            }
            data = swapStream.toByteArray()
            return Base64.getEncoder().encodeToString(data)
        } catch (_: IOException) {
        } finally {
            try {
                inputStream.close()
            } catch (e: IOException) {
                throw RuntimeException("输入流关闭异常", e)
            }
        }
        return null
    }

    /**
     * 字节数组转字符串，如 A0 09 70 -> 101000000000100101110000。
     * @param bts 转入字节数组。
     * @return 转换好的只有“1”和“0”的字符串。
     */
    private fun bytes2String(bts: ByteArray): String {
        val dic = arrayOf(
            "0000", "0001", "0010", "0011", "0100", "0101", "0110", "0111",
            "1000", "1001", "1010", "1011", "1100", "1101", "1110", "1111"
        )
        val out = StringBuilder()
        for (b in bts) {
            var s = String.format("%x", b)
            s = if (s.length == 1) "0$s" else s
            out.append(dic[s.substring(0, 1).toInt(16)])
            out.append(dic[s.substring(1, 2).toInt(16)])
        }
        return out.toString()
    }

    fun toImgCQCode(string: String): String {
        var base64: String?
        try {
            base64 = getBase64FromInputStream(toImg(string))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        return "[CQ:image,file=base64://$base64]"
    }

    fun toFile(string: String): File {
        try {
            val inputStream: InputStream = ByteArrayInputStream(toImg(string))
            val image = ImageIO.read(inputStream)
            val file = File.createTempFile("PlumBot", ".png")
            ImageIO.write(image, "png", file)
            file.deleteOnExit()
            return file
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    fun toImgBinary(string: String): String {
        var bytes: String
        try {
            bytes = bytes2String(toImg(string))
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return bytes
    }

    @Throws(IOException::class)
    fun toImgBinArray(string: String): ByteArray {
        return toImg(string)
    }
}
