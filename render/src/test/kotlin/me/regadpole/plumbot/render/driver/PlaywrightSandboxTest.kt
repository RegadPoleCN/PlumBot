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

package me.regadpole.plumbot.render.driver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **沙箱默认开启，容器例外必须显式**。本机无法真的启动 Chromium，故只断言启动参数与失败判定
 * 这两段纯逻辑 —— 它们正是"防线是否生效"与"失败是否被静默吞掉"的开关。
 */
class PlaywrightSandboxTest {

    @Test
    fun `sandbox is on by default and passes no sandbox disabling flags`() {
        assertEquals(emptyList(), PlaywrightSandbox.args(sandboxDisabled = false))
        assertFalse(
            PlaywrightSandbox.args(sandboxDisabled = false).contains(PlaywrightSandbox.NO_SANDBOX),
            "默认不得传 --no-sandbox（此前无条件传，等于防线从未生效）"
        )
    }

    @Test
    fun `explicit opt out passes both disabling flags`() {
        val args = PlaywrightSandbox.args(sandboxDisabled = true)

        assertTrue(args.contains(PlaywrightSandbox.NO_SANDBOX))
        assertTrue(args.contains(PlaywrightSandbox.NO_SETUID_SANDBOX), "setuid helper 也要一并关掉，否则容器里仍会失败")
    }

    @Test
    fun `recognizes sandbox failures from typical messages`() {
        listOf(
            "Failed to launch chromium: No usable sandbox!",
            "[ERROR] The SUID sandbox helper binary was found, but is not configured correctly.",
            "clone() failed: Operation not permitted",
            "bwrap: Creating new namespace failed",
        ).forEach { message ->
            assertTrue(PlaywrightSandbox.looksLikeSandboxFailure(message), "应识别为沙箱问题：$message")
        }
    }

    @Test
    fun `does not misclassify unrelated failures`() {
        listOf(
            null,
            "",
            "Executable doesn't exist at /root/.cache/ms-playwright/chromium/chrome",
            "net::ERR_NAME_NOT_RESOLVED",
        ).forEach { message ->
            assertFalse(PlaywrightSandbox.looksLikeSandboxFailure(message), "不应误判：$message")
        }
    }

    @Test
    fun `the hint asks for an explicit opt out instead of silently disabling the sandbox`() {
        val hint = PlaywrightSandbox.sandboxFailureHint()

        assertTrue(hint.contains("disable_sandbox"), "指引必须给出确切的配置键：$hint")
        assertTrue(hint.contains("显式"), "必须强调是显式操作（自动降级等于拆掉防线）：$hint")
        assertTrue(hint.contains("纯文本"), "要说明失败时的可见后果（不会静默无图）：$hint")
    }
}
