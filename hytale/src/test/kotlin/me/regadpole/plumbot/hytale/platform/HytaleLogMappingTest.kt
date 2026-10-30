// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import me.regadpole.plumbot.api.platform.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HytaleLogMappingTest {

    @Test
    fun `debug 开启时 DEBUG 以 INFO 输出并加前缀`() {
        val line = HytaleLogMapping.map(LogLevel.DEBUG, "下载模板", debugEnabled = true)

        assertEquals(HytaleConsoleLevel.INFO, line.level, "DEBUG 必须落在宿主默认可见的 INFO 上")
        assertEquals("[DEBUG] 下载模板", line.text)
        assertTrue(line.appendToDebugFile, "DEBUG 仍然要进 debug.log")
    }

    @Test
    fun `debug 开启时 TRACE 以 INFO 输出并加前缀`() {
        val line = HytaleLogMapping.map(LogLevel.TRACE, "响应码 200", debugEnabled = true)

        assertEquals(HytaleConsoleLevel.INFO, line.level)
        assertEquals("[TRACE] 响应码 200", line.text)
        assertTrue(line.appendToDebugFile)
    }

    @Test
    fun `debug 关闭时 DEBUG 保持 FINE 且不改文本`() {
        val line = HytaleLogMapping.map(LogLevel.DEBUG, "下载模板", debugEnabled = false)

        assertEquals(HytaleConsoleLevel.FINE, line.level, "未开启 debug 时不应把调试日志抬到 INFO 刷屏")
        assertEquals("下载模板", line.text)
        assertTrue(line.appendToDebugFile)
    }

    @Test
    fun `debug 关闭时 TRACE 保持 FINEST 且不改文本`() {
        val line = HytaleLogMapping.map(LogLevel.TRACE, "逐跳重定向", debugEnabled = false)

        assertEquals(HytaleConsoleLevel.FINEST, line.level)
        assertEquals("逐跳重定向", line.text)
        assertTrue(line.appendToDebugFile)
    }

    @Test
    fun `INFO 与 WARN 不被改写也不进 debug 文件`() {
        val info = HytaleLogMapping.map(LogLevel.INFO, "已装载渲染引擎", debugEnabled = true)
        assertEquals(HytaleConsoleLevel.INFO, info.level)
        assertEquals("已装载渲染引擎", info.text)
        assertFalse(info.appendToDebugFile, "INFO 不属于调试日志")

        val warn = HytaleLogMapping.map(LogLevel.WARN, "模板校验失败", debugEnabled = true)
        assertEquals(HytaleConsoleLevel.WARNING, warn.level)
        assertEquals("模板校验失败", warn.text)
        assertFalse(warn.appendToDebugFile)
    }

    @Test
    fun `ERROR 与 FATAL 均为 SEVERE`() {
        assertEquals(
            HytaleConsoleLevel.SEVERE,
            HytaleLogMapping.map(LogLevel.ERROR, "boom", debugEnabled = false).level
        )
        assertEquals(
            HytaleConsoleLevel.SEVERE,
            HytaleLogMapping.map(LogLevel.FATAL, "boom", debugEnabled = false).level
        )
    }

    @Test
    fun `除 DEBUG TRACE 外的级别文本永不改写`() {
        val plain = listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.FATAL)
        for (level in plain) {
            for (debug in listOf(true, false)) {
                val line = HytaleLogMapping.map(level, "原文", debugEnabled = debug)
                assertEquals("原文", line.text, "$level（debug=$debug）的文本被改写了")
                assertFalse(line.appendToDebugFile, "$level（debug=$debug）不应进 debug.log")
            }
        }
    }

    @Test
    fun `每个级别都有确定的宿主级别`() {
        for (level in LogLevel.entries) {
            for (debug in listOf(true, false)) {
                val line = HytaleLogMapping.map(level, "x", debugEnabled = debug)
                assertTrue(
                    line.level == HytaleConsoleLevel.INFO || line.level == HytaleConsoleLevel.FINE ||
                        line.level == HytaleConsoleLevel.FINEST || line.level == HytaleConsoleLevel.WARNING ||
                        line.level == HytaleConsoleLevel.SEVERE,
                    "$level（debug=$debug）没有可用的宿主级别"
                )
            }
        }
    }
}
