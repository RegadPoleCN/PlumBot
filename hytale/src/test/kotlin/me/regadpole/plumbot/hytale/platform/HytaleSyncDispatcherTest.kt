// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HytaleSyncDispatcherTest {

    @Test
    fun `任务排入世界队列时不内联执行`() {
        val queued = mutableListOf<Runnable>()
        var ran = 0
        val dispatcher = HytaleSyncDispatcher(
            enqueueWorldTask = { runnable -> queued.add(runnable); true },
            runOnFallback = { it.run() }
        )

        val result = dispatcher.dispatch { ran++ }

        assertTrue(result, "入队成功应返回 true")
        assertEquals(0, ran, "runSync 不得在调用线程内联执行任务")
        assertEquals(1, queued.size)

        queued.single().run()
        assertEquals(1, ran, "任务应在世界线程被消费时执行，且恰好一次")
    }

    @Test
    fun `拿不到世界时回退异步并报告一次`() {
        val fallbackRan = mutableListOf<Int>()
        var warnCount = 0
        var executed = 0
        val dispatcher = HytaleSyncDispatcher(
            enqueueWorldTask = { false },
            runOnFallback = { runnable -> fallbackRan.add(1); runnable.run() },
            onFallbackUsed = { warnCount++ }
        )

        val result = dispatcher.dispatch { executed++ }

        assertFalse(result, "回退路径应返回 false 让调用方知道没有真同步")
        assertEquals(1, fallbackRan.size)
        assertEquals(1, executed, "回退后任务仍必须执行，且只执行一次")
        assertEquals(1, warnCount)
    }

    @Test
    fun `世界 execute 抛异常等价于不可用`() {
        var executed = 0
        var warnCount = 0
        val dispatcher = HytaleSyncDispatcher(
            enqueueWorldTask = { throw IllegalStateException("world shutting down") },
            runOnFallback = { it.run() },
            onFallbackUsed = { warnCount++ }
        )

        val result = dispatcher.dispatch { executed++ }

        assertFalse(result)
        assertEquals(1, executed, "异常不能吞掉任务")
        assertEquals(1, warnCount)
    }
}
