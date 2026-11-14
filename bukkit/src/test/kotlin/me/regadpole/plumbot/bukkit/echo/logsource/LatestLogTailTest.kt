// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo.logsource

import me.regadpole.plumbot.bukkit.echo.capture.EchoSession
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.test.Test
import kotlin.test.assertTrue

class LatestLogTailTest {

    /** 指令触发日志风暴（增量超过单次 1MB 预算）时，紧跟风暴尾部的最新报错行必须仍被捕获。 */
    @Test
    fun `a burst larger than the read budget still captures the newest tail lines`() {
        val dir = Files.createTempDirectory("plumbot-logtail-test")
        val log = dir.resolve("latest.log")
        try {
            Files.write(log, listOf("seed line"), StandardOpenOption.CREATE)
            val source = LatestLogTail(log)
            assertTrue(source.start(), "种子文件可读")

            // 风暴：追加约 1.5MB 填充行，关键报错行在风暴之后（旧实现会把它丢掉）
            val filler = ("x".repeat(120) + "\n").toByteArray(Charsets.UTF_8)
            for (i in 0 until 13_000) {
                Files.write(log, filler, StandardOpenOption.APPEND)
            }
            Files.write(log, listOf("KEY-ERROR-LINE boom"), StandardOpenOption.APPEND)

            val session = EchoSession(System.currentTimeMillis(), includeLogPrefix = false)
            source.finish(session)
            source.finish(session)  // 第二次不应重复报错行

            val lines = session.snapshot()
            assertTrue(lines.any { it.contains("KEY-ERROR-LINE") }, "最新尾行必须保留，实际拿到 " + lines.size + " 行")
            assertTrue(lines.count { it.contains("KEY-ERROR-LINE") } == 1, "尾行不得重复捕获")
        } finally {
            Files.walk(dir).use { it.sorted( java.util.Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }

    /** 常规增量（未超预算）行为不变：只读新增部分。 */
    @Test
    fun `normal increment reads only newly appended bytes`() {
        val dir = Files.createTempDirectory("plumbot-logtail-test")
        val log = dir.resolve("latest.log")
        try {
            Files.write(log, listOf("first"), StandardOpenOption.CREATE)
            val source = LatestLogTail(log)
            assertTrue(source.start())
            Files.write(log, listOf("second"), StandardOpenOption.APPEND)

            val session = EchoSession(System.currentTimeMillis(), includeLogPrefix = false)
            source.finish(session)

            val lines = session.snapshot()
            assertTrue(lines.contains("second"), "新增行必须被读取")
            assertTrue(!lines.contains("first"), "种子行不属于本次增量")
        } finally {
            Files.walk(dir).use { it.sorted( java.util.Comparator.reverseOrder()).forEach { p -> Files.deleteIfExists(p) } }
        }
    }
}
