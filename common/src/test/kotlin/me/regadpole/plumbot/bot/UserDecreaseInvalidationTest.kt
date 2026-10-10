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

import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 退群时**成员缓存失效**的接线测试。
 *
 * ## 背景（这正是一处因"读代码"才发现的不对称）
 *
 * 三个适配器监听器（OneBot、MiraiMC-Bukkit、MiraiMC-Velocity）在收到成员退群事件时
 * 都汇聚到 `BotEventDispatcher.dispatchUserDecrease`，但**只有 OneBot 自己失效了缓存**：
 *
 * ```
 * OneBotListener.kt:47              onebot.groupMemberCache.invalidate(...)   ← 有
 * MiraiMCListener.kt:48-56          （无）
 * VelocityMiraiMCListener.kt:48-56  （无）
 * ```
 *
 * 后果：MiraiMC 下退群者会在 `GroupMemberCache` 的写入过期（默认 30 分钟）内
 * 继续被判定为"仍在群内"。现在失效逻辑收敛到中心分发点，本测试锁住它不再跑偏。
 */
class UserDecreaseInvalidationTest {

    private val factoryType = "test-invalidation-bot"
    private lateinit var bot: RecordingBot

    private class RecordingBot : IBot {
        val invalidated = mutableListOf<Pair<Long, Long>>()

        override var handler: BotHandler? = null
        override val metadata = BotAdapterMetadata(type = "recording", displayName = "Recording")

        override fun invalidateGroupMember(groupId: Long, userId: Long) {
            invalidated += groupId to userId
        }

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = false
        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) = Unit
        override fun sendGroupMsg(targetId: Long, message: String) = Unit
        override fun sendUserMsg(targetId: Long, message: String) = Unit
        override fun sendGroupPicWithText(targetId: Long, message: String) = Unit
        override fun sendUserPicWithText(targetId: Long, message: String) = Unit
        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card$targetId"
        override fun sendGroupImage(targetId: Long, imageFile: File) = Unit
        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) = Unit
        override fun sendGroupMsgAtAll(targetId: Long, message: String) = Unit
    }

    private class FakeConfig : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class FakeContext : PlatformContext {
        override val config: PlatformConfig = FakeConfig()
        override val datasource: PlatformConfig = FakeConfig()
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = TODO("测试未使用")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    @BeforeTest
    fun setUp() {
        BotEventDispatcher.clearAllListeners()
        bot = RecordingBot()
        BotProvider.registerFactory(object : BotFactory {
            override val metadata = BotAdapterMetadata(type = factoryType, displayName = "Test")
            override fun create(context: PlatformContext): IBot = bot
        })
        BotProvider.loadBot(FakeContext(), factoryType)
    }

    @AfterTest
    fun tearDown() {
        BotProvider.unloadBot()
        BotProvider.unregisterFactory(factoryType)
        BotEventDispatcher.clearAllListeners()
    }

    @Test
    fun `退群时适配器收到成员缓存失效通知`() {
        var listenerFired = false
        val handle = BotEventDispatcher.registerMemberDecreaseHandler { listenerFired = true }

        BotEventDispatcher.dispatchUserDecrease(groupId = 700L, userId = 42L)

        assertEquals(
            listOf(700L to 42L),
            bot.invalidated,
            "退群必须让适配器失效本地成员缓存，否则退群者会在缓存 TTL 内仍被判为在群"
        )
        assertTrue(listenerFired, "公共 API 事件监听器仍应正常触发")
        handle.close()
    }

    @Test
    fun `未加载机器人时派发退群不抛异常`() {
        BotProvider.unloadBot()

        // 旧实现在此依赖 ?. 保护；新增的失效调用必须保持同样的容错
        BotEventDispatcher.dispatchUserDecrease(1L, 2L)
    }
}
