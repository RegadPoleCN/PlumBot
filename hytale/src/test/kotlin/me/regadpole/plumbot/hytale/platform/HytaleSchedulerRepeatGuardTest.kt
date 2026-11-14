// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * 周期任务的异常隔离契约：一个坏任务只丢当前周期，循环继续；shutdown 之后循环停止。
 */
class HytaleSchedulerRepeatGuardTest {

    @Test
    fun `a failing repeating task only loses the current period`() {
        val warnings = CopyOnWriteArrayList<String>()
        val scheduler = HytalePlatformScheduler(warn = { warnings += it })
        val executed = CopyOnWriteArrayList<Int>()
        val counter = AtomicInteger()
        val latch = CountDownLatch(2)

        val handle = scheduler.runRepeatingAsync(0.milliseconds, 20.milliseconds) {
            val n = counter.incrementAndGet()
            executed += n
            latch.countDown()
            if (n == 1) error("boom")
        }

        try {
            assertTrue(latch.await(3, TimeUnit.SECONDS), "抛错后循环必须继续运行到下一周期：$executed")
            assertTrue(executed.contains(2), "第二周期必须继续执行：$executed")
            assertTrue(warnings.any { it.contains("周期任务执行异常") }, "要留一条告警：$warnings")
            assertTrue(warnings.any { it.contains("boom") }, "告警要带上原因：$warnings")
        } finally {
            handle.cancel()
            scheduler.shutdown()
        }
    }

    @Test
    fun `shutdown stops the repeating loop`() {
        val scheduler = HytalePlatformScheduler(warn = {})
        val counter = AtomicInteger()

        scheduler.runRepeatingAsync(0.milliseconds, 10.milliseconds) { counter.incrementAndGet() }
        Thread.sleep(50)
        scheduler.shutdown()
        val observed = counter.get()
        Thread.sleep(120)

        assertEquals(observed, counter.get(), "shutdown 之后循环必须停止")
    }
}
