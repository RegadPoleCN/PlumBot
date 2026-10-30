// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HytaleTickThreads] 的记账语义：装载器的主线程守卫依赖这张表。
 *
 * 表错了就会"永远不拦"或"永远拦"，两种都会让下载重新跑上 tick 线程。
 */
class HytaleTickThreadsTest {

    @AfterTest
    fun reset() {
        HytaleTickThreads.clear()
    }

    @Test
    fun `a thread that ran a world task is treated as the game thread`() {
        var marked = false
        var seenAsGameThread = false
        val worker = Thread({
            HytaleTickThreads.markCurrentThreadIsTickThread()
            marked = true
            seenAsGameThread = HytaleTickThreads.isOnGameThread()
        }, "plumbot-test-world-tick").also { it.isDaemon = true }

        worker.start()
        worker.join()

        assertTrue(marked)
        assertTrue(seenAsGameThread, "登记之后本线程必须命中")
        assertFalse(HytaleTickThreads.isOnGameThread(), "测试线程没跑过世界任务，不应命中")
    }

    @Test
    fun `clear forgets previous registrations`() {
        var markedBeforeClear = false
        var seenAfterClear = true
        val worker = Thread({
            HytaleTickThreads.markCurrentThreadIsTickThread()
            markedBeforeClear = HytaleTickThreads.isOnGameThread()
            HytaleTickThreads.clear()
            seenAfterClear = HytaleTickThreads.isOnGameThread()
        }, "plumbot-test-world-tick-2").also { it.isDaemon = true }

        worker.start()
        worker.join()

        assertTrue(markedBeforeClear, "登记之后必须命中")
        assertFalse(seenAfterClear, "clear 之后同一线程必须不再命中")
    }

    @Test
    fun `an unmarked thread is not the game thread`() {
        assertFalse(HytaleTickThreads.isOnGameThread(), "未登记线程不得被误判为 tick 线程")
    }
}
