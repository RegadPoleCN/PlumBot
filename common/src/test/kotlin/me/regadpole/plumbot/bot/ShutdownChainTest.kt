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

import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A08 / R08 验收：**关闭链必须"关得干净"** —— 反复启停不留线程与作用域。
 *
 * 两条断言一强一弱，互补：
 * 1. **强（确定性）**：每一轮的处理器的 `shutdown()` 都被调用，且关闭后不留作用域、不留登记项；
 * 2. **弱（启发式）**：20 轮启停后活线程数不显著增长（容忍池化线程的滞后回收）。
 */
class ShutdownChainTest {

    @AfterTest
    fun cleanup() {
        ShutdownRegistry.clear()
    }

    @Test
    fun `every start-stop cycle releases the handler and the scope`() {
        val context = FakePlatformContext()
        val handlers = mutableListOf<RecordingHandler>()

        repeat(20) {
            val bot = FakeBot(context)
            bot.start()
            val handler = RecordingHandler()
            bot.handler = handler
            handlers += handler
            bot.shutdown()

            assertNull(bot.currentRenderScopeForTest(), "第 $it 轮关闭后不得留下作用域")
        }

        assertEquals(20, handlers.count { it.shutdownCalled }, "每一轮的处理器都必须被释放（否则内置服务的作用域泄漏）")
    }

    @Test
    fun `twenty start-stop cycles do not grow the live thread count`() {
        val context = FakePlatformContext()
        // 先跑一轮让协程/IO 线程池完成首次创建，避免把"首建"算作泄漏
        FakeBot(context).apply { start(); shutdown() }
        Thread.sleep(200)
        val before = Thread.activeCount()

        repeat(20) {
            FakeBot(context).apply { start(); shutdown() }
        }
        Thread.sleep(300)
        val after = Thread.activeCount()

        assertTrue(
            after <= before + 8,
            "20 轮启停后活线程数不应显著增长（before=$before after=$after）—— 显著增长即存在作用域/线程泄漏"
        )
    }

    private class RecordingHandler : BotHandler {
        var shutdownCalled = false

        override fun shutdown() {
            shutdownCalled = true
        }

        override fun onGroupMessage(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistApply(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistQuery(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistRemove(message: String, groupId: Long, userId: Long) = Unit
        override fun onPlayerList(message: String, groupId: Long, userId: Long) = Unit
        override fun onUserDecrease(groupId: Long, userId: Long) = Unit
    }
}
