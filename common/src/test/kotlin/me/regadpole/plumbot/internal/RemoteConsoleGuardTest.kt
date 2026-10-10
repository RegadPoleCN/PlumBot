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
import kotlin.test.assertTrue

/**
 * R05 验收：远程控制台的**两道闸门**。
 *
 * 时间源注入 → 不需要真的等待；并发计数用"不释放"来模拟指令仍在跑。
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
        // 并发上限放到很大：本用例测的是**补桶**，不该被并发闸门挡住（首次写测试时正是被它误伤）
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
}
