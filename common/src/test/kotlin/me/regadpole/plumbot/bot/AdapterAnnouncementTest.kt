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

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 生命周期广播的**时序**契约：
 * 1. 只有**首次确认在线**后才补发一次 `load`，重连不重复 —— 否则 `client.action()` 抛
 *    `WebsocketNotConnectedException`，异常沿 renderJobs 协程冒到宿主日志；
 * 2. 从未在线则关闭时**不发** `unload` —— 否则同样抛异常，且根本没人能收到；
 * 3. 发送失败只记 WARN，不向调用方抛异常 —— 否则一次降级发送失败就是未处理的协程异常。
 */
class AdapterAnnouncementTest {

    private fun context() = FakePlatformContext(
        mapOf(
            "feature.load.enable" to true,
            "groups" to listOf(100L),
        )
    )

    private fun botWithAsyncStart(onlineAfterStart: Boolean = false): FakeBot =
        FakeBot(context()).also { it.onlineAfterStart = onlineAfterStart }

    @Test
    fun `load broadcast waits until the bot is actually online`() {
        val bot = botWithAsyncStart()
        val expected = Messages.load

        bot.start()
        assertTrue(bot.sentMessages.isEmpty(), "连接建立前不得发送 load 广播（旧实现会在此抛异常）")

        bot.markOnlineForTest()
        assertEquals(listOf(expected), bot.sentMessages, "确认在线后应补发且只补发一次 load 广播")
    }

    @Test
    fun `reconnect does not repeat the load broadcast`() {
        val bot = botWithAsyncStart()
        bot.start()
        bot.markOnlineForTest()

        bot.markRetryingForTest("WebSocket 断开")
        bot.markOnlineForTest()

        assertEquals(1, bot.sentMessages.size, "重连只恢复连接，不应重复广播 load")
    }

    @Test
    fun `unload broadcast is skipped when the bot never came online`() {
        val bot = botWithAsyncStart()
        bot.start()

        bot.shutdown()

        assertTrue(bot.sentMessages.isEmpty(), "从未在线时关闭不得尝试发送 unload（必然失败且无人接收）")
    }

    @Test
    fun `unload broadcast is sent after an online session`() {
        val bot = botWithAsyncStart()
        bot.start()
        bot.markOnlineForTest()
        val expected = Messages.unload

        bot.shutdown()

        assertTrue(expected in bot.sentMessages, "在线过之后关闭应广播 unload")
    }

    @Test
    fun `send failure does not escape the lifecycle call`() {
        val bot = botWithAsyncStart()
        bot.failOnSend = true
        bot.start()

        // 此处的发送异常必须被吞掉，不得抛进监控协程 / 宿主日志
        bot.markOnlineForTest()

        assertTrue(
            bot.context.logs.any { it.contains("纯文本发送失败") },
            "发送失败必须被捕获并留下 WARN，而不是冒泡成未处理异常"
        )
    }

    @Test
    fun `shutdown after failed startup does not broadcast`() {
        val bot = botWithAsyncStart()
        bot.start()
        bot.markFailedForTest("连接不可恢复")

        bot.shutdown()

        assertTrue(bot.sentMessages.isEmpty(), "启动失败（未在线）时关闭不得发送 unload")
    }
}
