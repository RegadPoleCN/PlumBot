package me.regadpole.plumbot.tool

import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.severe
import java.awt.*
import java.awt.image.BufferedImage
import java.io.*
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageOutputStream


class TextToImg {

    companion object {
        private var font: Font? = null
        private var fm: FontMetrics? = null
        private val ttfFile: File = File(getDataFolder(), "MiSans-Normal.ttf")

        @Throws(IOException::class)
        private fun toImg(text: String): ByteArray {
            if (fm == null) {
                try {
                    font = Font.createFont(Font.TRUETYPE_FONT, ttfFile.toURI().toURL().openStream())
                    font = font!!.deriveFont(32f)
                    fm = Toolkit.getDefaultToolkit().getFontMetrics(font)
                } catch (e: FontFormatException) {
                    throw RuntimeException(e)
                }
            }

            val strings = text.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var minX = 0
            for (line in strings) {
                var line = line
                line = line.replace("ﾧ\\S".toRegex(), "")
                line = line.replace("§\\S".toRegex(), "")

                //font.getLineMetrics(line)
                val result = fm!!.stringWidth(line)

                if (minX < result) minX = result
            }
            val Y = strings.size * 34 + (strings.size - 1) * 8 + 15
            minX += 32
            val image = BufferedImage(
                minX, Y,
                BufferedImage.TYPE_INT_BGR
            )
            val g = image.graphics
            g.setClip(0, 0, minX, Y)
            g.color = Color("FFFFFF".toInt(16))
            g.fillRect(0, 0, minX, Y) // 先用黑色填充整张图片,也就是背景
            g.color = Color.black // 在换成黑色
            g.font = font // 设置画笔字体
            for (i in strings.indices) {
                val nowLine = strings[i]
                var dex = 0
                var nowX = 16

                var j = 0
                while (j < nowLine.length) {
                    if (nowLine[j] == 'ﾧ' || nowLine[j] == '§') {
                        when (nowLine[j + 1]) {
                            '0' -> g.color = Color.black
                            '1' -> g.color = Color("0000AA".toInt(16))
                            '2' -> g.color = Color("00AA00".toInt(16))
                            '3' -> g.color = Color("00AAAA".toInt(16))
                            '4' -> g.color = Color("AA0000".toInt(16))
                            '5' -> g.color = Color("AA00AA".toInt(16))
                            '6' -> g.color = Color("FFAA00".toInt(16))
                            '7' -> g.color = Color("AAAAAA".toInt(16))
                            '8' -> g.color = Color("555555".toInt(16))
                            '9' -> g.color = Color("5555FF".toInt(16))
                            'a' -> g.color = Color("55FF55".toInt(16))
                            'b' -> g.color = Color("55FFFF".toInt(16))
                            'c' -> g.color = Color("FF5555".toInt(16))
                            'd' -> g.color = Color("FF55FF".toInt(16))
                            'e' -> g.color = Color("FFFF55".toInt(16))
                            'f' -> //                            g.setColor(new Color(Integer.parseInt("FFFFFF", 16)));
                                g.color = Color.black

                            'g' -> g.color = Color("DDD605".toInt(16))
                            else -> g.color = Color.black
                        }
                        j++
                        dex += 2
                    } else {
                        g.drawString(nowLine[dex].toString(), nowX, if (i > 0) 34 + (i) * 34 + (i) * 8 else 34)
                        nowX += fm!!.charWidth(nowLine[dex])
                        dex++
                    }
                    j++
                }

                //System.out.println(strings[i]);
            }
            g.dispose()
            val os = ByteArrayOutputStream()

            // 创建一个MemoryCacheImageOutputStream对象，传入os作为参数
            val mcios = MemoryCacheImageOutputStream(os)
            // 使用ImageIO.write方法将image写入mcios，指定图片格式为png
            ImageIO.write(image, "png", mcios)
            // 关闭mcios
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
            // 将图片文件转化为字节数组字符串，并对其进行Base64编码处理
            val data: ByteArray

            val inputStream: InputStream = ByteArrayInputStream(bytes)

            // 读取图片字节数组
            try {
                val swapStream = ByteArrayOutputStream()
                val buff = ByteArray(100)
                var rc: Int
                while ((inputStream.read(buff, 0, 100).also { rc = it }) > 0) {
                    swapStream.write(buff, 0, rc)
                }
                data = swapStream.toByteArray()
                return Base64.getEncoder().encodeToString(data)
            } catch (e: IOException) {
                severe(e.toString())
            } finally {
                try {
                    inputStream.close()
                } catch (e: IOException) {
                    throw Exception("输入流关闭异常")
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
            var base64: String? = null
            try {
                base64 = getBase64FromInputStream(toImg(string))
            } catch (e: Exception) {
                severe(e.toString())
            }
            return "[CQ:image,file=base64://$base64]"
        }

        fun toImgBinary(string: String): String {
            var bytes = ""
            try {
                bytes = bytes2String(toImg(string))
            } catch (e: IOException) {
                severe(e.toString())
            }
            return bytes
        }

        @Throws(IOException::class)
        fun toImgBinArray(string: String): ByteArray {
            return toImg(string)
        }
    }
}