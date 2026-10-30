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

import me.regadpole.plumbot.api.bot.BotState
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **连接状态机**：固定迁移路径与"失败不留中间态"。
 *
 * 布尔值无法表达"已加载但正在重连"，曾让"消息发不出去"时对外仍报"已连接"。
 */
class AdapterStateMachineTest {

    @Test
    fun `starts in INIT and becomes ONLINE for a synchronous adapter`() {
        val bot = FakeBot(FakePlatformContext())
        assertEquals(BotState.INIT, bot.state, "构造后应为 INIT")
        assertFalse(bot.isOnline())

        bot.start()

        assertEquals(BotState.ONLINE, bot.state)
        assertTrue(bot.isOnline(), "isOnline 必须基于状态推导")
    }

    @Test
    fun `an adapter that never confirms stays CONNECTING`() {
        val bot = FakeBot(FakePlatformContext()).apply { onlineAfterStart = false }
        bot.start()

        assertEquals(BotState.CONNECTING, bot.state, "未确认连接的适配器不得被当作在线")
        assertFalse(bot.isOnline())
    }

    @Test
    fun `retrying is not usable and can return to online`() {
        val bot = FakeBot(FakePlatformContext())
        bot.start()

        bot.markRetryingForTest("连接中断")
        assertEquals(BotState.RETRYING, bot.state)
        assertFalse(bot.isOnline(), "重连期间必须报不可用（否则调用方会以为消息能发出去）")

        bot.markOnlineForTest()
        assertEquals(BotState.ONLINE, bot.state)
        assertTrue(bot.isOnline())
    }

    @Test
    fun `a failed start ends in FAILED and does not swallow the error`() {
        val bot = FakeBot(FakePlatformContext()).apply {
            failOnStart = IllegalStateException("凭据无效")
        }

        assertFailsWith<IllegalStateException> { bot.start() }

        assertEquals(BotState.FAILED, bot.state, "启动失败不得停在 CONNECTING")
        assertTrue(bot.state.isTerminal)
    }

    @Test
    fun `shutdown reaches STOPPED and a failed shutdown reaches FAILED`() {
        val healthy = FakeBot(FakePlatformContext())
        healthy.start()
        healthy.shutdown()
        assertEquals(BotState.STOPPED, healthy.state)

        val broken = FakeBot(FakePlatformContext()).apply {
            failOnShutdown = IllegalStateException("断开失败")
        }
        broken.start()
        assertFailsWith<IllegalStateException> { broken.shutdown() }
        assertEquals(BotState.FAILED, broken.state, "关闭失败不得停在 STOPPING")
    }

    @Test
    fun `transitions are logged once per change`() {
        val context = FakePlatformContext()
        val bot = FakeBot(context)

        val before = context.logs.size
        bot.markOnlineForTest() // INIT → ONLINE：有变化，应记录
        val afterFirst = context.logs.size
        bot.markOnlineForTest() // 同状态：不应重复记录
        val afterSecond = context.logs.size

        assertTrue(afterFirst > before, "状态变化必须留下可诊断的日志")
        assertEquals(afterFirst, afterSecond, "同状态迁移不得打日志（热路径会被刷满）")
        assertTrue(context.logs.any { it.contains("INIT → ONLINE") }, "日志应写明迁移方向：${context.logs}")
    }
}
