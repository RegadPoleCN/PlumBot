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

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.render.RenderRoute
import kotlinx.datetime.Instant
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 白名单"删除 / 管理员自助申请 / 查询空结果"的**输出形态**回归测试。
 *
 * 这些结果此前统一走 `RenderRoute.TEXT`（经 `sendBindTemplate`），而它的门控 `render.outputs.text`
 * 默认为 `false`，于是删除成功等回复会**静默**退化成纯文本（只有 DEBUG 日志里有 `RENDER_DISABLED`）。
 * 现在改走 `RenderRoute.BIND_LIST` / `RenderRoute.WHITELIST`，由 `render.outputs.bind` 统一控制，
 * 文字兜底与卡片正文同源（同一份 `Messages` 模板与占位符替换结果）。
 *
 * 断言只能比较**可观察等价物**：`RenderProvider.portal` 是 `private @Volatile`，common 模块没有注入点，
 * 故测试核对真正发出的文本与 `RenderCards.plain(...)`（标题 + 正文）一致，并确认全程没有发图；
 * 任何一处回退成裸 `sendBindTemplate`，这里立刻失败。
 */
class BindRemoveCommandRenderTest {

    private val groupId = 100L
    private val userId = 2001L
    private val targetUserId = 3003L

    private lateinit var database: InMemoryDatabase
    private lateinit var context: FakeContext
    private lateinit var bot: FakeBot

    /** 模板是全局可写状态，测试修改后必须还原，避免污染同模块其它用例。 */
    private val savedTemplates = mutableMapOf<String, String>()

    @BeforeTest
    fun setUp() {
        savedTemplates["playerAddBind"] = Messages.playerAddBind
        savedTemplates["playerDeleteBind"] = Messages.playerDeleteBind
        savedTemplates["adminDeleteBind"] = Messages.adminDeleteBind
        savedTemplates["qqEmptyBind"] = Messages.qqEmptyBind
        savedTemplates["bindTimeLine"] = Messages.bindTimeLine
        savedTemplates["bindLimitLine"] = Messages.bindLimitLine
        savedTemplates["bindPlayersLine"] = Messages.bindPlayersLine

        // 用确定性模板替换 messages.yml 的默认占位符，让断言能直接核对"卡片正文"。
        Messages.playerAddBind = "已添加 %target_player%"
        Messages.playerDeleteBind = "已删除 %target_player%"
        Messages.adminDeleteBind = "已为 %user_name% 删除 %target_player%"
        Messages.qqEmptyBind = "你还没有绑定"
        Messages.bindTimeLine = "绑定于 %bind_time%"
        Messages.bindLimitLine = "上限 %limit%"
        Messages.bindPlayersLine = "名单 %players%"

        database = InMemoryDatabase()
        DatabaseProvider.start(database)
        context = FakeContext(FakeConfig())
        bot = FakeBot()
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
        Messages.playerAddBind = savedTemplates.getValue("playerAddBind")
        Messages.playerDeleteBind = savedTemplates.getValue("playerDeleteBind")
        Messages.adminDeleteBind = savedTemplates.getValue("adminDeleteBind")
        Messages.qqEmptyBind = savedTemplates.getValue("qqEmptyBind")
        Messages.bindTimeLine = savedTemplates.getValue("bindTimeLine")
        Messages.bindLimitLine = savedTemplates.getValue("bindLimitLine")
        Messages.bindPlayersLine = savedTemplates.getValue("bindPlayersLine")
    }

    @Test
    fun `删除成功后按 bindList 卡片投影发送`() {
        seed("Alex", "Steve")

        BindRemoveCommand(BotCommandService(context, bot)).execute("Steve", groupId, userId)

        val remaining = DatabaseProvider.getBindByUser(userId.toString()).keys.toList()
        assertEquals(listOf("Alex"), remaining, "只应删掉指定的那一条绑定")

        val expected = RenderCards.plain(
            RenderCards.bindList(
                text = "已删除 Steve",
                title = "白名单已删除 · Steve",
                userId = userId.toString(),
                players = remaining,
                limit = 5
            )
        )
        assertEquals(
            listOf(expected),
            bot.sentMessages,
            "删除结果必须走 bindList 卡片（render.outputs.bind 门控），而不是裸文字模板"
        )
        assertTrue(expected.contains("上限 5"), "卡片应补上名额上限事实行，实际：$expected")
        assertTrue(expected.contains("名单 Alex"), "卡片应补上剩余绑定名单，实际：$expected")
        assertEquals(listOf(false), bot.picFlags, "没有渲染门户时必须以纯文本标记发出")
        assertEquals(0, bot.imageSends, "没有渲染门户时不得发送图片")
        assertEquals(listOf("Steve"), context.fakePlayerService.kicked, "被删绑定的角色应被踢出服务器")
    }

    @Test
    fun `bind 图片开关关闭时删除仍完成且不出图`() {
        context.set("render.outputs.bind", false)
        seed("Steve")

        BindRemoveCommand(BotCommandService(context, bot)).execute("Steve", groupId, userId)

        assertTrue(database.bindings.isEmpty(), "开关只影响输出形态，绝不能让删除本身失败")

        val expected = RenderCards.plain(
            RenderCards.bindList(
                text = "已删除 Steve",
                title = "白名单已删除 · Steve",
                userId = userId.toString(),
                players = emptyList(),
                limit = 5
            )
        )
        assertEquals(
            listOf(expected),
            bot.sentMessages,
            "开关关闭时的降级文本必须与卡片正文同源（而不是另一份模板）"
        )
        assertEquals(listOf(false), bot.picFlags)
        assertEquals(0, bot.imageSends, "开关关闭时不得尝试出图")
    }

    @Test
    fun `管理员自助申请走 whitelist 卡片`() {
        context.set("admins", listOf(userId))

        BindApplyCommand(BotCommandService(context, bot)).execute("Steve", groupId, userId)

        assertEquals(listOf("Steve"), database.bindings.map { it.playerName }, "绑定必须真的落库")
        assertEquals(
            listOf("已添加 Steve"),
            bot.sentMessages,
            "whitelist 卡片的纯文本兜底就是 playerAddBind 的投影（题头与绑定时间只存在于卡片里）"
        )
        assertEquals(
            listOf(false),
            bot.picFlags,
            "若仍是 sendBindTemplate，会以 isPic=true 请求 TEXT 卡片（render.outputs.text 默认 false = 纯文字）"
        )
    }

    @Test
    fun `我的白名单为空时按 bindList 空列表卡片发送`() {
        BindQueryCommand(BotCommandService(context, bot)).execute("", groupId, userId)

        val expected = RenderCards.plain(
            RenderCards.bindList(
                text = "你还没有绑定",
                title = "我的白名单",
                userId = userId.toString(),
                players = emptyList(),
                limit = 5
            )
        )
        assertEquals(listOf(expected), bot.sentMessages, "空列表也应走 bindList，而不是裸模板")
        assertEquals(listOf(false), bot.picFlags)
    }

    @Test
    fun `管理员查询该 QQ 未绑定时也走 bindList 卡片`() {
        context.set("admins", listOf(userId))

        BindQueryCommand(BotCommandService(context, bot)).execute("qq:$targetUserId", groupId, userId)

        val expected = RenderCards.plain(
            RenderCards.bindList(
                text = "你还没有绑定",
                title = "白名单查询 · user$targetUserId",
                userId = targetUserId.toString(),
                players = emptyList(),
                limit = 5
            )
        )
        assertEquals(listOf(expected), bot.sentMessages, "空结果仍是列表语义，应与列表结果同一张卡片")
        assertEquals(listOf(false), bot.picFlags)
    }

    /**
     * 门控**接线**本身也钉住：`bindList`/`whitelist` 共用 `render.outputs.bind`（默认 true），
     * 而旧通道 `TEXT` 挂在 `render.outputs.text`（默认 false）上 —— 这正是
     * "申请/删除变成纯文字、开关看起来没坏"的根因：TEXT 的门控是**另一把默认关闭的开关**。
     */
    @Test
    fun `门控接线：bindList 与 whitelist 共用 bind 开关`() {
        assertEquals("bind", RenderRoute.BIND_LIST.featureGate, "删除/列表卡片必须挂在 bind 开关上")
        assertEquals("bind", RenderRoute.WHITELIST.featureGate, "单人绑定卡片同样挂 bind")
        assertEquals("text", RenderRoute.TEXT.featureGate, "旧通道 TEXT 由默认关闭的 render.outputs.text 控制")
    }

    private fun seed(vararg names: String) {
        names.forEach { database.addBind(userId.toString(), it) }
    }

    private class FakeConfig : PlatformConfig {
        private val values = mutableMapOf<String, Any>(
            "feature.bind.enable" to true,
            "feature.bind.maxNum" to 5,
            "feature.bind.adminBypass" to true,
            "render.outputs.bind" to true
        )

        fun set(key: String, value: Any) {
            values[key] = value
        }

        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")

        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> =
            @Suppress("UNCHECKED_CAST")
            (values[key(path)] as? List<Long>) ?: emptyList()

        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    /**
     * 真实的玩家服务替身。
     *
     * 不能用 `TODO("测试未使用")`：`AbstractWhitelistCommand.execute` 会捕获一切异常并改发
     * `internalError`，踢人抛出的 `NotImplementedError` 会被静默吞掉，测试就再也看不到
     * "删除成功"的输出，反而把失败伪装成通过。
     */
    private class FakePlayerService : PlatformPlayerService {
        val kicked = mutableListOf<String>()

        override fun kickPlayer(name: String) {
            kicked += name
        }

        override fun listPlayers(): List<String> = emptyList()

        override fun listPlayerString(): String = ""
    }

    private class FakeContext(private val fakeConfig: FakeConfig) : PlatformContext {
        val logs = mutableListOf<String>()
        val fakePlayerService = FakePlayerService()

        fun set(key: String, value: Any) = fakeConfig.set(key, value)

        override val config: PlatformConfig get() = fakeConfig
        override val datasource: PlatformConfig get() = fakeConfig
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = fakePlayerService
        override val platformType: PlatformType get() = TODO("测试未使用")

        override fun log(level: LogLevel, message: String) {
            logs += "$level $message"
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class FakeBot : IBot {
        val sentMessages = mutableListOf<String>()

        /** 每次 `sendMsg` 的 `isPic` 标记；纯文本路径必须全部是 false。 */
        val picFlags = mutableListOf<Boolean>()

        /** 真正发送图片的次数：门控关闭/无门户时必须为 0。 */
        var imageSends = 0

        override var handler: BotHandler? = null

        override val metadata: BotAdapterMetadata
            get() = BotAdapterMetadata(
                type = "fake",
                displayName = "Fake",
                // GROUP_MEMBER_QUERY：`%user_name%` 占位符由 userReplacements/originReplacements 取群名片，
                // 缺少该能力时 requireCapability 会抛异常并被 execute 吞成 internalError。
                capabilities = setOf(
                    BotCapability.GROUP_MESSAGE_SEND,
                    BotCapability.IMAGE_SEND,
                    BotCapability.GROUP_MEMBER_QUERY
                )
            )

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true

        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
            message?.let {
                sentMessages += it
                picFlags += isPic
            }
        }

        override fun sendGroupMsg(targetId: Long, message: String) {
            sentMessages += message
            picFlags += false
        }

        override fun sendUserMsg(targetId: Long, message: String) {
            sentMessages += message
            picFlags += false
        }

        override fun sendGroupPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun sendUserPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card$targetId"

        override fun sendGroupImage(targetId: Long, imageFile: File): Boolean {
            imageSends++
            return true
        }

        override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) = Unit
        override fun sendGroupMsgAtAll(targetId: Long, message: String) = Unit
    }

    private class InMemoryDatabase : IDatabase {
        val bindings = mutableListOf<Binding>()
        private var nextId = 1

        override fun initialize() = Unit
        override fun close() = Unit
        override fun getConnection(): DataSource = TODO("测试未使用")

        override fun getByUser(user: String): List<Binding> =
            bindings.filter { it.userId == user }

        override fun getByName(name: String): Binding? =
            bindings.firstOrNull { it.playerName.equals(name, ignoreCase = true) }

        override fun getById(id: Int): Binding? = bindings.firstOrNull { it.id == id }

        override fun addBind(user: String, name: String) {
            bindings += Binding(nextId++, user, name, null, Instant.fromEpochMilliseconds(0))
        }

        override fun removeBindByNum(user: String, id: Int): String? {
            val target = bindings.firstOrNull { it.userId == user && it.id == id } ?: return null
            bindings.remove(target)
            return target.playerName
        }

        override fun removeBindByUser(user: String) {
            bindings.removeIf { it.userId == user }
        }

        override fun removeBind(name: String) {
            bindings.removeIf { it.playerName.equals(name, ignoreCase = true) }
        }

        override fun setUUID(name: String, uuid: UUID) = Unit
    }
}
