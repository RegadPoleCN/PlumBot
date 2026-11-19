// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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

/**
 * 进群时**负缓存失效**的接线测试。
 *
 * 方案 B（ABSENT 哨兵负缓存）后，"不在群内"的结果会被缓存至 TTL（默认 30 分钟）。
 * 若进群不失效，新成员会在 TTL 内仍被判为不在群。三个适配器监听器的进群事件统一汇聚到
 * `BotEventDispatcher.dispatchUserIncrease`，本测试锁住中心失效不再跑偏。
 */
class UserIncreaseInvalidationTest {

    private val factoryType = "test-increase-invalidation-bot"
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
        override fun sendGroupImage(targetId: Long, imageFile: File) = true
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
    fun `进群时适配器收到成员缓存失效通知`() {
        BotEventDispatcher.dispatchUserIncrease(groupId = 700L, userId = 42L)

        assertEquals(
            listOf(700L to 42L),
            bot.invalidated,
            "进群必须让适配器失效'不在群内'的负缓存，否则新成员会在缓存 TTL 内仍被判为不在群"
        )
    }

    @Test
    fun `未加载机器人时派发进群不抛异常`() {
        BotProvider.unloadBot()

        // 与 dispatchUserDecrease 相同的 ?. 容错
        BotEventDispatcher.dispatchUserIncrease(1L, 2L)
    }
}
