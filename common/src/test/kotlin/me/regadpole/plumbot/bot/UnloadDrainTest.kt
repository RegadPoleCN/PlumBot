// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot

import kotlinx.coroutines.delay
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 关闭时的**有界排空**。
 *
 * 停服公告本身就是异步出图任务：`shutdown()` 派发后若立刻 `cancel()`，通知随时可能被取消
 * （表现为"停服公告有时发得出去、有时发不出去"）；但排空必须**有界**，渲染卡住时不能把
 * 平台关服线程一起拖死。测试走**与生产相同**的派发路径（`launchTracked`），断言真实行为。
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

        // **取消是异步的**：cancel() 返回后 finally 不一定已执行，直接断言会偶发失败（全量并行时尤甚）。
        val waitUntil = System.currentTimeMillis() + 2_000
        while (!cancelled && System.currentTimeMillis() < waitUntil) Thread.sleep(20)
        assertTrue(cancelled, "超时后剩余任务应被取消（而不是继续挂着）")
    }
}
