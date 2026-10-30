// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 远程控制台审计文案：一条用例覆盖两个分支与**唯一真正重要的断言**——关闭记录时不得泄露参数值。
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
