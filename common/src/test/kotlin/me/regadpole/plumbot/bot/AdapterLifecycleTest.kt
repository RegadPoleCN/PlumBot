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
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 适配器生命周期：出图作用域与处理器资源都必须随关闭释放。
 *
 * 出图作用域"关闭即取消并置空、下次出图拿新实例"——做成 `by lazy` 单例时，停一次 Bot 后所有
 * 出图都会因作用域已取消而静默失败；`shutdown()` 必须让处理器释放自持资源（内置服务的协程
 * 作用域），只清空 `handler` 引用的话那些作用域再也无法回收。
 */
class AdapterLifecycleTest {

    @Test
    fun `render scope is cancelled and rebuilt across a restart`() {
        val bot = FakeBot(FakePlatformContext())

        bot.start()
        val first = bot.renderScopeForTest()
        assertTrue(first.isActive, "启动后作用域应处于活动状态")
        assertSame(first, bot.renderScopeForTest(), "同一生命周期内应复用同一作用域")

        bot.shutdown()
        assertNull(bot.currentRenderScopeForTest(), "shutdown 必须把作用域置空（否则会复用已取消的实例）")

        bot.start()
        val second = bot.renderScopeForTest()
        assertNotSame(first, second, "重启后必须是全新作用域")
        assertTrue(second.isActive, "新作用域必须可用")
    }

    @Test
    fun `shutdown releases the handler-owned resources`() {
        val bot = FakeBot(FakePlatformContext())
        bot.start()

        val handler = RecordingShutdownHandler()
        bot.handler = handler

        bot.shutdown()

        assertTrue(handler.shutdownCalled, "适配器关闭时必须调用 handler.shutdown()，否则内置服务的作用域会泄漏")
        assertTrue(bot.doShutdownCalled, "子类关闭逻辑仍要执行")
    }

    @Test
    fun `shutdown before start is harmless`() {
        // 未初始化就关闭（插件加载失败 / 用户禁用 Bot）不得抛错，也不得留下作用域
        val bot = FakeBot(FakePlatformContext())

        bot.shutdown()
        bot.shutdown()

        assertNull(bot.currentRenderScopeForTest(), "未启动就关闭不得创建或残留作用域")
    }

    @Test
    fun `events go to the new handler after a restart`() {
        // 重启后**不得复用旧的处理器实例**：旧实例的资源已释放，继续持有它会让事件落进
        // 已关闭的作用域/已清空的缓存，表现为"看着在线却什么都不响应"
        val context = FakePlatformContext()
        val bot = FakeBot(context)
        BotProvider.registerFactory(object : BotFactory {
            override val metadata = bot.metadata
            override fun create(context: PlatformContext) = bot
        })

        try {
            BotProvider.loadBot(context, bot.metadata.type)
            val first = bot.handler
            assertTrue(first != null, "启动后必须装上处理器")

            BotProvider.unloadBot()
            BotProvider.loadBot(context, bot.metadata.type)
            val second = bot.handler

            assertTrue(second != null, "重启后必须装上处理器")
            assertTrue(first !== second, "重启后必须是**新的处理器实例**，不得复用已释放的旧实例")
        } finally {
            BotProvider.unloadBot()
            BotProvider.clearFactories()
        }
    }

    @Test
    fun `shutdown is idempotent`() {
        val bot = FakeBot(FakePlatformContext())
        bot.start()

        bot.shutdown()
        bot.shutdown()

        assertNull(bot.currentRenderScopeForTest(), "重复 shutdown 不得留下作用域")
    }

    private class RecordingShutdownHandler : BotHandler {
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
