// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

import kotlin.test.assertEquals
import kotlin.test.Test
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.nio.file.Path

/**
 * render() 的单遍占位符替换契约：替换值里的 "%xxx%" 不得被二次展开（防注入），
 * 未命中的占位符原样保留，同一占位符重复传入时取最后一个值（与旧 fold 语义一致）。
 */
class RenderPlaceholderInjectionTest {

    private val service = BotCommandService(FakeContext(), RecordingBot())

    @Test
    fun `replacement values cannot inject further placeholders`() {
        val out = service.render("%a% says %b%", "%a%" to "%b%!", "%b%" to "real")
        assertEquals("%b%! says real", out)
    }

    @Test
    fun `unknown placeholders survive untouched`() {
        val out = service.render("hi %a% %unknown%", "%a%" to "1")
        assertEquals("hi 1 %unknown%", out)
    }

    @Test
    fun `last value wins when a placeholder repeats`() {
        val out = service.render("v=%a%", "%a%" to "first", "%a%" to "second")
        assertEquals("v=second", out)
    }

    private class FakeContext : PlatformContext {
        private val values = mutableMapOf<String, Any>(
            "command.prefix" to "/",
        )

        private val holder: PlatformConfig = object : PlatformConfig {
            private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")
            override fun getString(vararg path: String?): String? = values[key(path)] as? String
            override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
            override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
            override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
            override fun getLongList(vararg path: String?): List<Long> =
                @Suppress("UNCHECKED_CAST")
                (values[key(path)] as? List<Long>) ?: emptyList()
            override fun getStringList(vararg path: String?): List<String?> =
                @Suppress("UNCHECKED_CAST")
                ((values[key(path)] as? List<Any?>) ?: emptyList()).map { it as? String }
            override fun getSubConfig(vararg path: String?): PlatformConfig = this
        }

        override val config: PlatformConfig get() = holder
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = TODO("测试未使用")
        override val platformType: PlatformType get() = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class RecordingBot : IBot {
        override var handler: BotHandler? = null
        override val metadata: BotAdapterMetadata = BotAdapterMetadata(
            type = "fake",
            displayName = "Fake",
            capabilities = setOf(
                BotCapability.GROUP_MESSAGE_RECEIVE,
                BotCapability.GROUP_MESSAGE_SEND,
                BotCapability.GROUP_MEMBER_QUERY,
            )
        )
        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) = Unit
        override fun sendGroupMsg(targetId: Long, message: String) = Unit
        override fun sendUserMsg(targetId: Long, message: String) = Unit
        override fun sendGroupPicWithText(targetId: Long, message: String) = Unit
        override fun sendUserPicWithText(targetId: Long, message: String) = Unit
        override fun sendGroupImage(targetId: Long, imageFile: java.io.File) = true
        override fun sendGroupImage(targetId: Long, imageBytes: ByteArray) = true
        override fun sendUserImage(targetId: Long, imageFile: java.io.File) = true
        override fun sendUserImage(targetId: Long, imageBytes: ByteArray) = true
        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) = Unit
        override fun sendGroupMsgAtAll(targetId: Long, message: String) = Unit
        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user-$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card-$targetId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true
        override fun invalidateGroupMember(groupId: Long, userId: Long) = Unit
    }
}