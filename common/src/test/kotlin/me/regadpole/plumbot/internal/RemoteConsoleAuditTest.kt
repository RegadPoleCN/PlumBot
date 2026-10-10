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
 * 远程控制台审计文案（决策 D9）。
 *
 * 验收：`log_commands=true` 时保留完整指令；`false` 时**不得出现参数值**，只留指令名与参数个数。
 */
class RemoteConsoleAuditTest {

    @Test
    fun `logs the full command when enabled`() {
        val text = RemoteConsoleAudit.describe("/whitelist add Steve", logCommands = true)

        assertTrue(text.contains("/whitelist add Steve"), "开启时必须记录完整指令：$text")
    }

    @Test
    fun `hides argument values when disabled`() {
        val text = RemoteConsoleAudit.describe("/whitelist add Steve secretReason", logCommands = false)

        assertFalse(text.contains("Steve"), "关闭时不得出现参数值：$text")
        assertFalse(text.contains("secretReason"), "关闭时不得出现参数值：$text")
        assertTrue(text.contains("whitelist"), "关闭时仍要保留指令名以便追溯：$text")
        assertTrue(text.contains("参数 3 个"), "关闭时应记录参数个数：$text")
    }

    @Test
    fun `handles a command without arguments`() {
        val text = RemoteConsoleAudit.describe("save-all", logCommands = false)

        assertTrue(text.contains("save-all"), "应保留指令名：$text")
        assertTrue(text.contains("参数 0 个"), "无参数应记 0：$text")
    }

    @Test
    fun `trims surrounding whitespace`() {
        val text = RemoteConsoleAudit.describe("   list   ", logCommands = true)

        assertEquals("[PlumBot-Security] 远程控制台 正在执行指令: /list", text)
    }
}
