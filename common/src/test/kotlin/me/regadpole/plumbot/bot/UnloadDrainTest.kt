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

package me.regadpole.plumbot.bot

import kotlinx.coroutines.delay
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A10 / R10 验收：**关闭时的"有界排空"**。
 *
 * 背景：停服公告本身就是异步出图任务。修复前 `shutdown()` 派发后立刻 `cancel()`，
 * 通知随时可能被取消 —— 表现为"停服公告有时发得出去、有时发不出去"。
 * 而排空又必须**有界**：渲染卡住时不能把平台关服线程一起拖死。
 *
 * 测试用**与生产完全相同**的派发路径（`launchTracked`），因此断言的是真实行为而非测试专用逻辑。
 */
class UnloadDrainTest {

    @Test
    fun `shutdown waits for a pending job to finish`() {
        val bot = FakeBot(FakePlatformContext())
        bot.start()

        var finished = false
        bot.launchTrackedForTest {
            delay(120)
            finished = true
        }

        val started = System.currentTimeMillis()
        bot.shutdown()
        val elapsed = System.currentTimeMillis() - started

        assertTrue(finished, "关闭时必须等待已派发任务完成（否则停服公告会被取消）")
        assertTrue(elapsed >= 100, "应确实等待了任务完成，而不是立即取消（elapsed=${elapsed}ms）")
    }

    @Test
    fun `the drain is bounded so a stuck render cannot block shutdown`() {
        val bot = FakeBot(FakePlatformContext())
        bot.start()

        var cancelled = false
        bot.launchTrackedForTest {
            try {
                delay(30_000)
            } finally {
                cancelled = true
            }
        }

        val started = System.currentTimeMillis()
        bot.shutdown()
        val elapsed = System.currentTimeMillis() - started

        // 上限放宽到 10s：断言的是"有界"这一性质（配置硬上限 2.5s），而不是精确耗时；
        // 并行跑全量测试时 JIT/GC 会让单次测量偏高，卡太紧会变成假失败。
        assertTrue(elapsed < 10_000, "排空必须有硬上限（配置 2.5s），实测 ${elapsed}ms，不得拖住关服线程")

        // **取消是异步的**：cancel() 返回后协程的 finally 不一定已经执行。
        // 之前直接断言会偶发失败（全量并行跑时尤其明显），这里给它一小段时间。
        val waitUntil = System.currentTimeMillis() + 2_000
        while (!cancelled && System.currentTimeMillis() < waitUntil) Thread.sleep(20)
        assertTrue(cancelled, "超时后剩余任务应被取消（而不是继续挂着）")
    }
}
