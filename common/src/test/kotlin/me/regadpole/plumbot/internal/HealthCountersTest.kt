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

import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 健康计数：只增不减、并发安全、快照覆盖全部键。
 */
class HealthCountersTest {

    @AfterTest
    fun cleanup() {
        HealthCounters.reset()
    }

    @Test
    fun `unset counters read as zero`() {
        assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK))
    }

    @Test
    fun `increment accumulates and snapshot exposes every key`() {
        // 断言**增量**而非绝对值：HealthCounters 是全局单例，同 JVM 内其它测试（如词库抓取）也会累加它，
        // 写死 5L 会让本用例随执行顺序随机失败。
        val before = HealthCounters.get(HealthCounters.Key.FILTER_HITS)

        HealthCounters.increment(HealthCounters.Key.FILTER_HITS)
        HealthCounters.increment(HealthCounters.Key.FILTER_HITS, 4)

        assertEquals(before + 5L, HealthCounters.get(HealthCounters.Key.FILTER_HITS))

        val snapshot = HealthCounters.snapshot()
        HealthCounters.Key.entries.forEach { key ->
            assertEquals(true, snapshot.containsKey(key.name), "快照必须包含 $key")
        }
    }

    @Test
    fun `concurrent increments are not lost`() {
        val threads = 8
        val perThread = 200
        val start = CountDownLatch(1)
        // 同样用增量断言：单例计数器可能已被同一 JVM 内的其它用例累加过。
        val key = HealthCounters.Key.RENDER_SEND_FAILED
        val before = HealthCounters.get(key)

        val workers = (1..threads).map {
            Thread {
                start.await()
                repeat(perThread) { HealthCounters.increment(key) }
            }.also { it.start() }
        }
        start.countDown()
        workers.forEach { it.join() }

        assertEquals(before + (threads * perThread).toLong(), HealthCounters.get(key))
    }

    @Test
    fun `reset clears counters`() {
        HealthCounters.increment(HealthCounters.Key.BOT_STARTED)
        HealthCounters.reset()

        assertEquals(0L, HealthCounters.get(HealthCounters.Key.BOT_STARTED))
    }
}
