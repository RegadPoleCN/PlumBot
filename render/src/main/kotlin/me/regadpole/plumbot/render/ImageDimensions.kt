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

/**
 * 图片**头部尺寸**解析（不完整解码）。
 *
 * ## 为什么需要
 * 头像/皮肤来自外部（图床、玩家皮肤服务），体积上限拦得住"大文件"，
 * 拦不住**解压炸弹**：一个几百 KB 的 PNG 可以声明 50000×50000 像素，
 * `ImageIO.read` 会按声明尺寸分配 ARGB 缓冲（约 10GB）—— 直接 OOM，把整个服务端带走。
 *
 * 因此解码**之前**先读头部尺寸，超过上限直接拒绝。只解析头部，不分配像素缓冲。
 */
internal object ImageDimensions {

    /** 默认像素上限（宽×高）。64M 像素 ≈ 256MB ARGB，已是单张头像的极端上限。 */
    const val DEFAULT_MAX_PIXELS = 64 * 1024 * 1024

    /**
     * 解析宽高；无法识别或头部不完整时返回 null（调用方应"未知则放行但受体积上限约束"）。
     */
    fun read(bytes: ByteArray): Pair<Int, Int>? = readPng(bytes) ?: readGif(bytes) ?: readJpeg(bytes)

    /** 是否超过像素上限。**尺寸未知时返回 false**（不误杀未知格式，交由体积上限兜底）。 */
    fun exceedsPixelBudget(bytes: ByteArray, maxPixels: Int = DEFAULT_MAX_PIXELS): Boolean {
        val (w, h) = read(bytes) ?: return false
        if (w <= 0 || h <= 0) return true
        return w.toLong() * h.toLong() > maxPixels.toLong()
    }

    /** PNG：8 字节签名 + 4 字节长度 + "IHDR" + 宽(4) + 高(4)，均大端。 */
    private fun readPng(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 24) return null
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        for (i in sig.indices) if (b[i] != sig[i]) return null
        if (!(b[12] == 'I'.code.toByte() && b[13] == 'H'.code.toByte() &&
                b[14] == 'D'.code.toByte() && b[15] == 'R'.code.toByte())
        ) {
            return null
        }
        return beInt(b, 16) to beInt(b, 20)
    }

    /** GIF：签名 "GIF8" + 宽(2, 小端) + 高(2, 小端)。 */
    private fun readGif(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 10) return null
        if (!(b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == '8'.code.toByte())) {
            return null
        }
        val w = (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8)
        val h = (b[8].toInt() and 0xFF) or ((b[9].toInt() and 0xFF) shl 8)
        return w to h
    }

    /** JPEG：扫描段，找到 SOF0/1/2 段（内含高、宽）即返回。 */
    private fun readJpeg(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 4) return null
        if (!(b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte())) return null

        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) {
                i++
                continue
            }
            val marker = b[i + 1].toInt() and 0xFF
            // SOF0..SOF15（排除 DHT=C4、JPG=C8、DAC=CC）
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val h = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val w = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                return w to h
            }
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) {
                i += 2
                continue
            }
            val length = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (length < 2) return null
            i += 2 + length
        }
        return null
    }

    private fun beInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)
}
