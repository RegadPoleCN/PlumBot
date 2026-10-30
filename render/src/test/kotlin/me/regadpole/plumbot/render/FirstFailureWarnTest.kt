// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.platform.LogLevel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 头像抓取的逐玩家失败降噪：只有**第一次**以 WARN 出现，后续同文本降为 DEBUG。
 */
class FirstFailureWarnTest {

    @Test
    fun `first failure is WARN and mentions the downgrade`() {
        val logs = mutableListOf<Pair<LogLevel, String>>()
        val throttle = FirstFailureWarn { level, message -> logs += level to message }

        throttle.report("[PlumBot-Render] 头像下载失败 (https://x/a.png): timeout")

        val (level, message) = logs.single()
        assertEquals(LogLevel.WARN, level, "首次失败必须可见")
        assertTrue(message.contains("后续同类明细降为 DEBUG"), "首次告警要说明后续去向：$message")
        assertTrue(message.contains("头像下载失败"), "首次告警必须保留原始原因：$message")
    }

    @Test
    fun `subsequent failures are DEBUG with the original text`() {
        val logs = mutableListOf<Pair<LogLevel, String>>()
        val throttle = FirstFailureWarn { level, message -> logs += level to message }

        repeat(3) { throttle.report("[PlumBot-Render] 响应内容不是图片，已拒绝：https://x/$it.png") }

        assertEquals(LogLevel.WARN, logs.first().first)
        assertEquals(listOf(LogLevel.DEBUG, LogLevel.DEBUG), logs.drop(1).map { it.first })
        assertTrue(logs.drop(1).all { it.second.contains("响应内容不是图片") }, "降级只改级别，不改文本：$logs")
    }

    @Test
    fun `concurrent reports still escalate exactly once`() {
        val warns = AtomicInteger()
        val throttle = FirstFailureWarn { level, _ -> if (level == LogLevel.WARN) warns.incrementAndGet() }
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            repeat(64) {
                pool.execute {
                    start.await()
                    throttle.report("[PlumBot-Render] 头像下载失败")
                }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "线程池未能按时结束")
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, warns.get(), "并发下 WARN 也只能出现一次")
    }
}
