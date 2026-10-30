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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 控制台输出的**归一化**：真实输出里混着颜色、清行、窗口标题与 `\r` 进度刷新，在 QQ 里是乱码，
 * 还会**占用截断额度**把有用内容挤掉。
 */
class ConsoleTextTest {

    @Test
    fun `strips colour and cursor sequences`() {
        val raw = "\u001B[32m[Server] 完成\u001B[0m\u001B[K"

        assertEquals("[Server] 完成", ConsoleText.sanitize(raw))
    }

    @Test
    fun `strips osc window titles`() {
        val raw = "\u001B]0;Minecraft Server\u0007[Server] 启动完成"

        assertEquals("[Server] 启动完成", ConsoleText.sanitize(raw))
    }

    @Test
    fun `strips single character escapes`() {
        assertEquals("abc", ConsoleText.sanitize("\u001B(Babc"))
    }

    @Test
    fun `normalizes carriage returns so progress bars do not look like noise`() {
        // 进度条：同一行用 \r 反复覆盖
        assertEquals("下载 100%", ConsoleText.sanitize("下载 10%\r下载 50%\r下载 100%"))
        // CRLF 仍应保留换行语义
        assertEquals("第一行\n第二行", ConsoleText.sanitize("第一行\r\n第二行"))
    }

    @Test
    fun `keeps plain text and ampersand colour codes untouched`() {
        val plain = "&a绿色 &c红色\n第二行"
        assertEquals(plain, ConsoleText.sanitize(plain))
        assertEquals("", ConsoleText.sanitize(""))
    }

    @Test
    fun `sanitizing before truncation keeps useful content`() {
        // 归一化若放在截断之后，控制序列会先吃掉额度：这里模拟"截断前清理"的正确顺序
        val noisy = "\u001B[32m".repeat(100) + "真正的内容"
        val maxLength = 8

        val cleanedFirst = ConsoleText.sanitize(noisy).take(maxLength)
        val truncatedFirst = noisy.take(maxLength)

        assertTrue(cleanedFirst.contains("真正"), "先清理再截断应保留真实内容：$cleanedFirst")
        assertFalse(truncatedFirst.contains("真正"), "先截断则只剩控制序列：$truncatedFirst")
    }
}
