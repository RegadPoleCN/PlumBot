// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 旧字符串契约的分类规则：输入文案由 [EchoResult.LEGACY_EXCEPTION_PREFIX] /
 * [EchoResult.LEGACY_UNKNOWN_MARKER] 常量拼接 —— 生产方（各平台 dispatchConsoleCommand
 * 的兜底分支）也引用同一常量，本测试等于把"两侧同源"钉死：改常量会同步改变分类输入，
 * 任何一侧单独改文案硬编码都会立刻红。
 */
class EchoResultLegacyContractTest {

    @Test
    fun blankLegacyOutputIsSuccessWithoutOutput() {
        val result = EchoResult.fromLegacyOutput("   ")
        assertEquals(EchoState.SUCCESS_NO_OUTPUT, result.state)
    }

    @Test
    fun exceptionPrefixClassifiesAsFailed() {
        val raw = EchoResult.LEGACY_EXCEPTION_PREFIX + ": 指令不存在"
        val result = EchoResult.fromLegacyOutput(raw)
        assertEquals(EchoState.FAILED, result.state)
        assertTrue(result.detail?.contains("指令不存在") == true, "异常细节应保留在 detail 里")
    }

    @Test
    fun unknownMarkerClassifiesAsUnknownOrRejected() {
        val raw = "指令执行" + EchoResult.LEGACY_UNKNOWN_MARKER + "此指令。"
        val result = EchoResult.fromLegacyOutput(raw)
        assertEquals(EchoState.UNKNOWN_OR_REJECTED, result.state)
    }

    @Test
    fun ordinaryConsoleTextIsCapturedVerbatim() {
        val raw = "[Server] Done (1.2s)!"
        val result = EchoResult.fromLegacyOutput(raw)
        assertEquals(EchoState.CAPTURED, result.state)
        assertEquals(raw, result.text)
    }
}