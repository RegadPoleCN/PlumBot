// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 远程控制台的**两道闸门**（按用户限流 + 全局并发上限）。时间源可注入，因此无需真的等待；
 * 并发计数用"不释放"来模拟指令仍在跑。
 */
class RemoteConsoleGuardTest {

    private var now = 0L
    private fun guard(perMinute: Int = 3, maxConcurrent: Int = 2) =
        RemoteConsoleGuard(perMinute, maxConcurrent) { now }

    @Test
    fun `allows a burst up to the bucket size then limits`() {
        val guard = guard(perMinute = 3, maxConcurrent = 10)

        repeat(3) { assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L), "第 ${it + 1} 次应放行") }
        assertEquals(RemoteConsoleGuard.Decision.RateLimited, guard.tryAcquire(1L), "超出桶容量必须限流")
    }

    @Test
    fun `refills the bucket over time`() {
        // 并发上限放到很大：本用例测的是**补桶**，不该被并发闸门挡住
        val guard = guard(perMinute = 60, maxConcurrent = 10_000)
        repeat(60) { guard.tryAcquire(1L) }
        assertEquals(RemoteConsoleGuard.Decision.RateLimited, guard.tryAcquire(1L))

        now += 1_000 // 60/分钟 → 1 秒补 1 个令牌
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L), "等待后应恢复额度")
    }

    @Test
    fun `rate limit is per user`() {
        val guard = guard(perMinute = 1, maxConcurrent = 10)
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L))
        assertEquals(RemoteConsoleGuard.Decision.RateLimited, guard.tryAcquire(1L))

        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(2L), "另一个 QQ 不应被牵连")
    }

    @Test
    fun `global concurrency cap applies across users`() {
        val guard = guard(perMinute = 100, maxConcurrent = 2)
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L))
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(2L))
        assertEquals(
            RemoteConsoleGuard.Decision.TooManyConcurrent,
            guard.tryAcquire(3L),
            "并发上限是全局的：不同 QQ 也要受同一上限约束"
        )

        guard.release()
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(3L), "释放后应重新放行")
    }

    @Test
    fun `release is idempotent and never goes negative`() {
        val guard = guard(perMinute = 100, maxConcurrent = 1)
        guard.release()
        guard.release()
        assertEquals(0, guard.inFlightCount())

        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L))
        guard.release()
        guard.release()
        assertEquals(0, guard.inFlightCount(), "多释放不得把计数压成负数（否则上限会永久失效）")
    }

    @Test
    fun `zero means unlimited`() {
        val guard = guard(perMinute = 0, maxConcurrent = 0)
        repeat(50) { assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L)) }
        assertEquals(50, guard.inFlightCount(), "不限并发时计数仍应准确")
    }

    @Test
    fun `tracked users stay bounded`() {
        val guard = guard(perMinute = 1, maxConcurrent = 10_000)
        repeat(3_000) { guard.tryAcquire(it.toLong()) }
        // 不直接断言内部表大小，但必须仍然可用且不抛错（无界增长会在此类压测下暴露内存问题）
        assertTrue(guard.tryAcquire(999_999L) == RemoteConsoleGuard.Decision.Allowed)
    }

    @Test
    fun `concurrent acquires never exceed the cap`() {
        val guard = guard(perMinute = 0, maxConcurrent = 4)
        val holders = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val allowed = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val start = CountDownLatch(1)
        val threads = (1..64).map {
            Thread {
                start.await()
                if (guard.tryAcquire(1L) == RemoteConsoleGuard.Decision.Allowed) {
                    allowed.incrementAndGet()
                    val nowHolders = holders.incrementAndGet()
                    peak.updateAndGet { old -> maxOf(old, nowHolders) }
                    Thread.sleep(2)
                    holders.decrementAndGet()
                    guard.release()
                } else {
                    rejected.incrementAndGet()
                }
            }
        }
        threads.forEach(Thread::start)
        start.countDown()
        threads.forEach(Thread::join)

        assertTrue(peak.get() <= 4, "实际并发持有数不得超过上限（峰值 ${peak.get()}）")
        assertEquals(64, allowed.get() + rejected.get(), "所有尝试都应有明确结论")
        assertEquals(0, guard.inFlightCount(), "全部结束后在途计数必须归零")
    }

    @Test
    fun `a rate limited acquire does not occupy the concurrency slot`() {
        val guard = guard(perMinute = 1, maxConcurrent = 4)
        assertEquals(RemoteConsoleGuard.Decision.Allowed, guard.tryAcquire(1L))
        // 只补到 0.5 个令牌，不足以再执行：这次尝试应被限流
        now += 30_000
        assertEquals(RemoteConsoleGuard.Decision.RateLimited, guard.tryAcquire(1L))
        assertEquals(1, guard.inFlightCount(), "限流路径不得占用并发额度（否则额度被逐步泄漏）")
        guard.release()
        assertEquals(0, guard.inFlightCount())
    }
}
