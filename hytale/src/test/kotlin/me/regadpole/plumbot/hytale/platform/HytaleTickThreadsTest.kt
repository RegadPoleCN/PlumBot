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
