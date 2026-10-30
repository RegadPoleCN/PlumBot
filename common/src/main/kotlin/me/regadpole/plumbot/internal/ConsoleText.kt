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

package me.regadpole.plumbot.internal

/**
 * 控制台输出的**文本归一化**。
 *
 * 服务端控制台输出里混着大量终端控制序列：颜色（`\u001B[32m`）、清行（`\u001B[K`）、窗口标题
 * （OSC `\u001B]0;…\u0007`）、进度条用的回车覆盖（`\r`）等。这些字节在 QQ 里**不是颜色**，
 * 而是一串乱码方块，破坏可读性，还会**占用截断额度**把真正有用的内容挤掉。
 *
 * 因此进聊天前先归一化：只做**无损可读化**，不改变实际文本内容。
 */
object ConsoleText {

    /** CSI：`ESC [ 参数 中间字节 终止字节`（覆盖颜色、光标、清行等）。 */
    private val CSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    /** OSC：`ESC ] … BEL` 或 `ESC ] … ESC \`（窗口标题、超链接等）。 */
    private val OSC = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)")

    /** 其它双字符转义：`ESC P/X/^/_ … ESC \`。 */
    private val DCS = Regex("\u001B[PX^_].*?\u001B\\\\", RegexOption.DOT_MATCHES_ALL)

    /** 单字符转义：`ESC` + 一个可见字符（如 `ESC M`）。 */
    private val SINGLE = Regex("\u001B[@-Z\\\\-_]")

    /** 字符集指定：`ESC ( B` / `ESC ) 0` 等（三字节，控制台与部分插件会输出）。 */
    private val CHARSET = Regex("\u001B[()*+#%][0-9A-Za-z]")

    /**
     * 去掉终端控制序列，并按**真实语义**归一化回车覆盖（`\r`）。
     *
     * `\r` 是"回到行首覆盖"，进度条会反复刷新同一行；保留全部中间态会让聊天里出现一大段噪声。
     * 因此**同一行只保留最后一次覆盖的内容**，`\r\n` 仍视为换行。
     */
    fun sanitize(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        result = OSC.replace(result, "")
        result = DCS.replace(result, "")
        result = CSI.replace(result, "")
        result = CHARSET.replace(result, "")
        result = SINGLE.replace(result, "")
        return normalizeCarriageReturns(result)
    }

    /** 按"回车覆盖"语义折叠每一行：只保留最后一次覆盖后的内容。 */
    private fun normalizeCarriageReturns(text: String): String {
        val out = StringBuilder(text.length)
        var line = StringBuilder()
        var index = 0
        while (index < text.length) {
            when (val ch = text[index]) {
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        out.append(line).append('\n')
                        line = StringBuilder()
                        index += 2
                        continue
                    }
                    // 孤立 \r：丢弃本行已积累的内容（被覆盖）
                    line = StringBuilder()
                }

                '\n' -> {
                    out.append(line).append('\n')
                    line = StringBuilder()
                }

                else -> line.append(ch)
            }
            index++
        }
        out.append(line)
        return out.toString()
    }
}
