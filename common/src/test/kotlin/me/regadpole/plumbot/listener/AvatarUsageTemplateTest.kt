// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.listener

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
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.config.Messages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 无参 avatar 的用法提示必须把 `%cmdprefix%` 换成真实前缀（而非原样显示）；
 * 昵称/名片这类用户可控值进入模板前必须转义 MiniMessage 标签。
 */
class AvatarUsageTemplateTest {

    private val groupId = 10000L
    private val userId = 20000L
    private val avatarUsageBackup = Messages.avatarUsage

    @AfterTest
    fun restoreMessages() {
        Messages.avatarUsage = avatarUsageBackup
    }

    @Test
    fun `bare avatar command renders the usage template with the real prefix`() {
        val ctx = FakeContext()
        ctx.set("feature.avatar.enable", true)
        val bot = RecordingBot()
        val handler = DefaultBotHandler(ctx, bot)

        Messages.avatarUsage = "用法：%cmdprefix%avatar <玩家名>"
        handler.onGroupMessage("/avatar", groupId, userId)

        val sent = bot.sentMessages.single()
        assertEquals("用法：/avatar <玩家名>", sent, "无参 avatar 必须显示带真实前缀的用法")
        assertTrue(!sent.contains('%'), "用法提示不得残留未替换的占位符")
    }

    @Test
    fun `user name and card are escaped before entering templates`() {
        val ctx = FakeContext()
        ctx.set("feature.avatar.enable", true)
        val bot = RecordingBot(userName = "<red>Steve", userCard = "<bold>Steve</bold>")
        val service = BotCommandService(ctx, bot)

        val replacements = service.userReplacements(groupId, userId).toMap()
        val escape: (String) -> String = { text -> MiniMessage.miniMessage().escapeTags(text) }

        val name = replacements.getValue("%user_name%")
        val nick = replacements.getValue("%user_nick%")
        assertEquals(escape("<red>Steve"), name, "昵称必须经过 MiniMessage 转义")
        assertEquals(escape("<bold>Steve</bold>"), nick, "名片必须经过 MiniMessage 转义")
        assertNotEquals("<red>Steve", name, "转义不得是恒等映射（否则断言无意义）")
    }

    private class FakeContext : PlatformContext {
        private val values = mutableMapOf<String, Any>(
            "feature.message.enable" to true,
            "feature.message.to_server" to true,
            "feature.message.mode" to 0,
            "feature.message.prefix" to "#",
            "feature.bind.enable" to true,
            "command.prefix" to "/",
            "groups" to listOf(10000L),
            // 命令键来自配置子节点（子配置在测试里折叠为同一层，故按叶子名取值）
            "addBind" to listOf("申请白名单"),
            "avatar" to listOf("avatar"),
        )

        fun set(key: String, value: Any) {
            values[key] = value
        }

        private val holder: PlatformConfig = newConfig()

        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")

        private fun newConfig(): PlatformConfig = object : PlatformConfig {
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

    private class RecordingBot(
        private val userName: String = "user-default",
        private val userCard: String = "card-default",
    ) : IBot {
        val sentMessages = mutableListOf<String>()

        override var handler: BotHandler? = null

        override val metadata: BotAdapterMetadata = BotAdapterMetadata(
            type = "fake",
            displayName = "Fake",
            capabilities = buildSet {
                add(BotCapability.GROUP_MESSAGE_RECEIVE)
                add(BotCapability.GROUP_MESSAGE_SEND)
                add(BotCapability.GROUP_MEMBER_QUERY)
            }
        )

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
            if (message != null) sentMessages += message
        }
        override fun sendGroupMsg(targetId: Long, message: String) { sentMessages += message }
        override fun sendUserMsg(targetId: Long, message: String) { sentMessages += message }
        override fun sendGroupPicWithText(targetId: Long, message: String) { sentMessages += message }
        override fun sendUserPicWithText(targetId: Long, message: String) { sentMessages += message }
        override fun sendGroupImage(targetId: Long, imageFile: java.io.File) = true
        override fun sendGroupImage(targetId: Long, imageBytes: ByteArray) = true
        override fun sendUserImage(targetId: Long, imageFile: java.io.File) = true
        override fun sendUserImage(targetId: Long, imageBytes: ByteArray) = true
        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) { sentMessages += message }
        override fun sendGroupMsgAtAll(targetId: Long, message: String) { sentMessages += message }

        override fun getGroupUserName(groupId: Long, targetId: Long): String = userName
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = userCard
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true
        override fun invalidateGroupMember(groupId: Long, userId: Long) = Unit
    }
}
