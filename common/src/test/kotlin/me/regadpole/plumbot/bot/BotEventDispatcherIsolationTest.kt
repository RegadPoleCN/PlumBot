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

import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A06 / R13 验收：**内置 handler 抛异常不得阻断第三方监听器**。
 *
 * 为什么重要：内置 handler 里是数据库、渲染、成员查询 —— 任何一处抛异常（例如库连接断了），
 * 修复前的实现会让异常直接冒泡，于是**所有扩展都收不到事件**且看不出原因。
 */
class BotEventDispatcherIsolationTest {

    @AfterTest
    fun cleanup() {
        BotProvider.unloadBot()
        BotProvider.clearFactories()
        BotEventDispatcher.clearAllListeners()
    }

    @Test
    fun `a throwing built-in handler does not stop third-party listeners`() {
        val context = FakePlatformContext()
        val bot = FakeBot(context)
        BotProvider.registerFactory(factoryOf(bot))
        BotProvider.loadBot(context, bot.metadata.type)

        // loadBot 会用内置 handler 覆盖，因此注入要在其之后
        bot.handler = ExplodingHandler()

        var received = 0
        val handle = BotEventDispatcher.registerGroupMessageHandler { received++ }

        BotEventDispatcher.dispatchGroupMessage("hello", groupId = 1L, senderId = 2L)

        assertEquals(1, received, "内置 handler 抛异常时，第三方监听器仍必须收到事件")
        handle.close()
    }

    @Test
    fun `a healthy built-in handler is still called first`() {
        val context = FakePlatformContext()
        val bot = FakeBot(context)
        BotProvider.registerFactory(factoryOf(bot))
        BotProvider.loadBot(context, bot.metadata.type)

        val recording = RecordingHandler()
        bot.handler = recording

        var received = 0
        val handle = BotEventDispatcher.registerGroupMessageHandler { received++ }

        BotEventDispatcher.dispatchGroupMessage("hi", groupId = 3L, senderId = 4L)

        assertEquals(1, recording.calls.size, "内置 handler 应被调用一次")
        assertEquals("hi", recording.calls.first())
        assertEquals(1, received)
        handle.close()
    }

    private fun factoryOf(bot: FakeBot): BotFactory = object : BotFactory {
        override val metadata = bot.metadata
        override fun create(context: PlatformContext) = bot
    }

    private class ExplodingHandler : BotHandler {
        override fun onGroupMessage(message: String, groupId: Long, userId: Long): Unit =
            throw IllegalStateException("built-in handler boom")

        override fun onWhitelistApply(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistQuery(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistRemove(message: String, groupId: Long, userId: Long) = Unit
        override fun onPlayerList(message: String, groupId: Long, userId: Long) = Unit
        override fun onUserDecrease(groupId: Long, userId: Long) = Unit
    }

    private class RecordingHandler : BotHandler {
        val calls = mutableListOf<String>()

        override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
            calls += message
        }

        override fun onWhitelistApply(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistQuery(message: String, groupId: Long, userId: Long) = Unit
        override fun onWhitelistRemove(message: String, groupId: Long, userId: Long) = Unit
        override fun onPlayerList(message: String, groupId: Long, userId: Long) = Unit
        override fun onUserDecrease(groupId: Long, userId: Long) = Unit
    }
}
