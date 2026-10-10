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
import net.kyori.adventure.text.Component
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 群消息命中命令但功能被关闭时，**不得回落到"当普通聊天转发"**。
 *
 * 背景：`handleCommand` 曾把"正则命中"与"功能开启"写在同一个条件里，
 * 功能关闭时返回 false，`onGroupMessage` 便继续遍历并最终调用消息转发 ——
 * 于是 `/申请白名单`、`/cmd op xxx` 这类文本会被当作聊天送到游戏内。
 */
class CommandGateForwardingTest {

    private val groupId = 10000L
    private val userId = 20000L

    @Test
    fun `disabled command is consumed instead of being forwarded as chat`() {
        val ctx = FakeContext()
        ctx.set("feature.bind.enable", false)
        val handler = DefaultBotHandler(ctx, FakeBot())

        handler.onGroupMessage("/申请白名单 Steve", groupId, userId)

        assertTrue(
            ctx.forwarded.isEmpty(),
            "功能关闭时命令文本不得进入游戏聊天，实际转发: ${ctx.forwarded}"
        )
    }

    @Test
    fun `plain chat is still forwarded`() {        val ctx = FakeContext()
        val handler = DefaultBotHandler(ctx, FakeBot())

        handler.onGroupMessage("hello world", groupId, userId)

        // 只断言"确实发生了转发"：转发文案来自 messages.yml 模板，单测不加载资源，
        // 因此不能断言正文内容（模板为空时渲染结果为空字符串）。
        assertEquals(1, ctx.forwarded.size, "普通聊天必须照常转发（对照组）")
    }

    @Test
    fun `feature gates read the reloaded config`() {
        val ctx = FakeContext()
        ctx.set("feature.bind.enable", false)
        val service = BotCommandService(ctx, FakeBot())
        assertFalse(service.isFeatureEnabled("bind"), "初始配置为关闭")

        ctx.set("feature.bind.enable", true)
        ctx.reload()

        assertTrue(
            service.isFeatureEnabled("bind"),
            "reload 后必须读到新配置（把 context.config 缓存成 val 会一直读到旧值）"
        )
    }

    @Test
    fun `command aliases take effect after a config reload`() {
        val ctx = FakeContext()
        // 关掉 bind 功能，使"命中命令"与"被当成聊天转发"在外部可区分
        ctx.set("feature.bind.enable", false)
        val handler = DefaultBotHandler(ctx, FakeBot())

        handler.onGroupMessage("/申请白名单 Steve", groupId, userId)
        assertTrue(ctx.forwarded.isEmpty(), "旧别名应被命令门控消费（不转发）")

        // 模拟修改别名后 /plumbot reload：命令表此前只在构造期解析一次，改别名要重启才生效
        ctx.set("addBind", listOf("新白名单"))
        ctx.reload()

        handler.onGroupMessage("/申请白名单 Steve", groupId, userId)
        assertEquals(1, ctx.forwarded.size, "reload 后旧别名必须失效（否则命令表仍是构造期快照）")

        handler.onGroupMessage("/新白名单 Steve", groupId, userId)
        assertEquals(1, ctx.forwarded.size, "reload 后新别名必须立即生效")
    }

    @Test
    fun `disabled avatar command is consumed instead of forwarded`() {
        val ctx = FakeContext()
        ctx.set("feature.avatar.enable", false)
        val handler = DefaultBotHandler(ctx, FakeBot())

        // A17：`feature.avatar.enable` 缺失时会读成 false，因此模板必须显式给出该键；
        // 关闭时命令必须被**消费**（而不是掉进聊天转发，把 `/avatar Steve` 送进服务器）
        handler.onGroupMessage("/avatar Steve", groupId, userId)

        assertTrue(ctx.forwarded.isEmpty(), "头像功能关闭时不得回落为聊天转发，实际转发: ${ctx.forwarded}")
    }

    private class FakeContext : PlatformContext {
        private val values = mutableMapOf<String, Any>(
            "feature.message.enable" to true,
            "feature.message.to_server" to true,
            "feature.message.mode" to 0,
            "feature.message.prefix" to "#",
            "feature.bind.enable" to true,
            "feature.cmdPrefix" to "/",
            "groups" to listOf(10000L),
            // 命令键来自配置子节点（子配置在测试里折叠为同一层，故按叶子名取值）
            "addBind" to listOf("申请白名单"),
            "avatar" to listOf("avatar"),
        )
        val forwarded = mutableListOf<String>()

        fun set(key: String, value: Any) {
            values[key] = value
        }

        /** 每次 reload 都会替换配置对象：用于验证读取方没有缓存旧引用。 */
        private var holder: PlatformConfig = newConfig()

        fun reload() {
            holder = newConfig()
        }

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
        override fun sendMessage(message: Component) {
            forwarded += message.toString()
        }

        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class FakeBot : IBot {
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
        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) = Unit
        override fun sendGroupMsg(targetId: Long, message: String) = Unit
        override fun sendUserMsg(targetId: Long, message: String) = Unit
        override fun sendGroupPicWithText(targetId: Long, message: String) = Unit
        override fun sendUserPicWithText(targetId: Long, message: String) = Unit
        override fun sendGroupImage(targetId: Long, imageFile: java.io.File) = Unit
        override fun sendGroupImage(targetId: Long, imageBytes: ByteArray) = Unit
        override fun sendUserImage(targetId: Long, imageFile: java.io.File) = Unit
        override fun sendUserImage(targetId: Long, imageBytes: ByteArray) = Unit
        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) = Unit
        override fun sendGroupMsgAtAll(targetId: Long, message: String) = Unit

        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user-$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card-$targetId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true
        override fun invalidateGroupMember(groupId: Long, userId: Long) = Unit
    }
}
