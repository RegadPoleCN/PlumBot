// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
import me.regadpole.plumbot.database.DatabaseProvider
import kotlinx.datetime.Instant
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `feature.bind.require_in_group` 的**接线测试**。
 *
 * 该配置键此前是"声明了但全仓零读取方"的死键，`IBot.checkUserInGroup` 也从未被业务层调用。
 * 本测试直接从指令入口驱动（`execute` 会先 `requireDatabase()`，故用内存库替身接管），
 * 因此能同时验证三件事：门控是否被调用、拒绝后是否真的没有落库、开关关闭时是否完全不产生额外开销。
 */
class BindApplyCommandGroupGateTest {

    private val groupId = 100L
    private val userId = 2001L
    private val targetUserId = 3003L

    private lateinit var database: InMemoryDatabase
    private lateinit var context: FakeContext
    private lateinit var bot: FakeBot

    @BeforeTest
    fun setUp() {
        database = InMemoryDatabase()
        DatabaseProvider.start(database)
        context = FakeContext(FakeConfig())
        bot = FakeBot()
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
    }

    private fun applyCommand(): BindApplyCommand =
        BindApplyCommand(BotCommandService(context, bot))

    private fun boundPlayerNames(): List<String> =
        database.bindings.map { it.playerName }

    // 门控**只作用于管理员代绑**（`#addBind <QQ> <玩家名>`）：
    // 自助申请者是在本群发出的消息，成员身份已由消息本身确立，再查一次没有新信息。

    @Test
    fun `代绑时目标不在群 则拒绝且不写入绑定`() {
        context.set("feature.bind.require_in_group", true)
        context.set("admins", listOf(userId))
        bot.memberInGroup = false

        applyCommand().execute("$targetUserId Steve", groupId, userId)

        assertEquals(1, bot.checkUserInGroupCalls.get(), "应调用成员校验")
        assertEquals(listOf(targetUserId to groupId), bot.checkedPairs, "校验的应是被代绑的 QQ")
        assertTrue(boundPlayerNames().isEmpty(), "被拒时不得写入绑定")
        // 断言拒绝文案本身（含 %user_id% 已被替换）。
        // 注意：测试中未加载 messages.yml，Messages 字段是 <missing:xxx> 占位符，
        // 因此这里比对的是"模板 + 替换结果"，而不是写死的中文文案。
        assertEquals(
            listOf(me.regadpole.plumbot.config.Messages.notInGroup.replace("%user_id%", targetUserId.toString())),
            bot.sentMessages,
            "应发出拒绝文案"
        )
    }

    @Test
    fun `代绑时目标在群 则放行并写入绑定`() {
        context.set("feature.bind.require_in_group", true)
        context.set("admins", listOf(userId))
        bot.memberInGroup = true

        applyCommand().execute("$targetUserId Steve", groupId, userId)

        assertEquals(1, bot.checkUserInGroupCalls.get())
        assertEquals(listOf("Steve"), boundPlayerNames())
    }

    @Test
    fun `代绑开关关闭时完全不调用成员校验`() {
        context.set("feature.bind.require_in_group", false)
        context.set("admins", listOf(userId))

        applyCommand().execute("$targetUserId Steve", groupId, userId)

        assertEquals(0, bot.checkUserInGroupCalls.get(), "关闭时不得产生任何成员校验开销")
        assertEquals(listOf("Steve"), boundPlayerNames())
    }

    @Test
    fun `代绑时适配器缺少成员校验能力 则放行并告警`() {
        context.set("feature.bind.require_in_group", true)
        context.set("admins", listOf(userId))
        bot.declareMemberCheck = false

        applyCommand().execute("$targetUserId Steve", groupId, userId)

        assertEquals(0, bot.checkUserInGroupCalls.get(), "无能力时无法校验")
        assertEquals(listOf("Steve"), boundPlayerNames(), "应 fail-open 放行")
        assertTrue(
            context.logs.any { it.contains("GROUP_MEMBER_CHECK") },
            "必须告警，否则服主无从察觉保护未生效。实际日志：${context.logs}"
        )
    }

    @Test
    fun `群员自助申请不触发成员校验`() {
        context.set("feature.bind.require_in_group", true)
        bot.memberInGroup = false // 即便查询会返回"不在群"，也不该被查

        applyCommand().execute("Steve", groupId, userId)

        assertEquals(
            0,
            bot.checkUserInGroupCalls.get(),
            "自助申请者刚在本群发过消息，成员身份已确立；查询只会带来超时误拒的风险"
        )
        assertEquals(listOf("Steve"), boundPlayerNames())
    }

    @Test
    fun `管理员自助申请不触发成员校验`() {
        context.set("feature.bind.require_in_group", true)
        context.set("admins", listOf(userId))
        bot.memberInGroup = false

        applyCommand().execute("Steve", groupId, userId)

        assertEquals(0, bot.checkUserInGroupCalls.get(), "管理员给自己绑同理，不应查询")
        assertEquals(listOf("Steve"), boundPlayerNames())
    }

    @Test
    fun `移除指令不受该开关影响`() {
        context.set("feature.bind.require_in_group", true)
        bot.memberInGroup = false
        database.bindings += Binding(1, userId.toString(), "Steve", null, Instant.fromEpochMilliseconds(0))

        BindRemoveCommand(BotCommandService(context, bot)).execute("Steve", groupId, userId)

        assertEquals(0, bot.checkUserInGroupCalls.get(), "移除路径不得被门控拦截")
        assertTrue(boundPlayerNames().isEmpty(), "绑定应被正常移除，否则用户会被锁死在自己的绑定之外")
    }

    private class FakeConfig : PlatformConfig {
        private val values = mutableMapOf<String, Any>(
            "feature.bind.enable" to true,
            "feature.bind.whitelist" to true,
            "feature.bind.maxNum" to 5,
            "feature.bind.adminBypass" to true,
            "feature.bind.require_in_group" to false
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

    private class FakeContext(private val fakeConfig: FakeConfig) : PlatformContext {
        val logs = mutableListOf<String>()

        fun set(key: String, value: Any) = fakeConfig.set(key, value)

        override val config: PlatformConfig get() = fakeConfig
        override val datasource: PlatformConfig get() = fakeConfig
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = TODO("测试未使用")
        override val platformType: PlatformType get() = TODO("测试未使用")

        override fun log(level: LogLevel, message: String) {
            logs += "$level $message"
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class FakeBot : IBot {
        var memberInGroup = true
        var declareMemberCheck = true
        val checkUserInGroupCalls = AtomicInteger()
        val checkedPairs = mutableListOf<Pair<Long, Long>>()
        val sentMessages = mutableListOf<String>()

        override var handler: BotHandler? = null

        override val metadata: BotAdapterMetadata
            get() = BotAdapterMetadata(
                type = "fake",
                displayName = "Fake",
                capabilities = buildSet {
                    add(BotCapability.GROUP_MESSAGE_SEND)
                    add(BotCapability.IMAGE_SEND)
                    if (declareMemberCheck) add(BotCapability.GROUP_MEMBER_CHECK)
                }
            )

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"

        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean {
            checkUserInGroupCalls.incrementAndGet()
            checkedPairs += userId to groupId
            return memberInGroup
        }

        override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
            message?.let { sentMessages += it }
        }

        override fun sendGroupMsg(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun sendUserMsg(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun sendGroupPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun sendUserPicWithText(targetId: Long, message: String) {
            sentMessages += message
        }

        override fun getGroupUserName(groupId: Long, targetId: Long): String = "user$targetId"
        override fun getGroupUserCard(groupId: Long, targetId: Long): String = "card$targetId"
        override fun sendGroupImage(targetId: Long, imageFile: File) = true
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
