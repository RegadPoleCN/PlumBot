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
 * 只保留一条用例：它同时覆盖两个分支与**唯一真正重要的断言**（关闭时不得泄露参数值）。
 * 原先 4 条里其余三条（"无参数记 0 个""首尾空格被裁剪"等）属实现细节，收益低于维护成本。
 */
class RemoteConsoleAuditTest {

    @Test
    fun `switches between the full command and a redacted summary`() {
        val full = RemoteConsoleAudit.describe("/whitelist add Steve", logCommands = true)
        assertTrue(full.contains("/whitelist add Steve"), "开启时必须记录完整指令：$full")

        val redacted = RemoteConsoleAudit.describe("/whitelist add Steve secretReason", logCommands = false)
        assertFalse(redacted.contains("Steve"), "关闭时不得出现参数值：$redacted")
        assertFalse(redacted.contains("secretReason"), "关闭时不得出现参数值：$redacted")
        assertTrue(redacted.contains("whitelist"), "关闭时仍要保留指令名以便追溯：$redacted")
        assertTrue(redacted.contains("参数 3 个"), "关闭时应记录参数个数：$redacted")

        assertEquals(
            "[PlumBot-Security] 远程控制台 正在执行指令: /list",
            RemoteConsoleAudit.describe("   list   ", logCommands = true),
            "首尾空格应被裁剪"
        )
    }
}
