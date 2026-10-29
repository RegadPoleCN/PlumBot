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
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 退群解绑的三分策略。
 *
 * 旧实现是"这个 QQ 的全部绑定统统解掉并把人踢了"，退一个群就顺带废掉他在其他群的绑定；
 * 现在只有 `source_group == 本群` 的行会被删除，来源群未知（NULL：升级前的历史数据、
 * 游戏内指令、外部 API 写入）与来自别的群的行都必须**原样保留**。
 *
 * 直接驱动 `BindUserDecreaseCommand.execute`（会先 `requireDatabase()`，用内存库替身接管）。
 */
class BindUserDecreasePolicyTest {

    private val database = InMemoryDatabase()

    @BeforeTest
    fun setUp() {
        database.bindings.clear()
        DatabaseProvider.start(database)
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
    }

    @Test
    fun `only bindings from the leaving group are unbound and kicked`() {
        // 每个用例用独立群号：告警去重表是进程内 companion 状态，跨用例会互相吞掉告警
        val groupId = 9101L
        val userId = 5001L
        database.seed(userId.toString(), "Steve", groupId.toString())
        database.seed(userId.toString(), "Alex", "9202")
        database.seed(userId.toString(), "Herobrine", null)
        val context = FakeContext()

        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, userId)

        assertEquals(listOf("Steve"), context.players.kicked, "只应踢出来自本群的那条绑定的角色")
        assertEquals(
            listOf("Alex", "Herobrine"),
            database.bindings.map { it.playerName },
            "来自别的群的绑定与来源群未知的绑定都必须保留"
        )
        assertTrue(
            context.logs.any { it.startsWith("INFO") && it.contains("解绑 1 条") },
            "必须留下一条 INFO 汇总（实际日志：${context.logs}）"
        )
        assertTrue(
            context.logs.any { it.startsWith("WARN") && it.contains("已保留") },
            "保留了来源群未知的绑定就必须告警，否则脏数据永远没人知道（实际日志：${context.logs}）"
        )
    }

    @Test
    fun `nothing is touched when the leaving group contributed no binding`() {
        val groupId = 9102L
        val userId = 5002L
        database.seed(userId.toString(), "Alex", "9202")
        database.seed(userId.toString(), "Herobrine", null)
        val context = FakeContext()

        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, userId)

        assertTrue(context.players.kicked.isEmpty(), "没有本群来源的绑定就不该踢人")
        assertEquals(2, database.bindings.size, "任何一条绑定都不该被删除")
        assertTrue(
            context.logs.any { it.startsWith("DEBUG") && it.contains("没有来自本群的绑定") },
            "这种情况下应是 DEBUG 而不是刷 WARN（实际日志：${context.logs}）"
        )
    }

    @Test
    fun `feature switch turned off leaves every binding alone`() {
        val groupId = 9103L
        val userId = 5003L
        database.seed(userId.toString(), "Steve", groupId.toString())
        val context = FakeContext()
        context.set("feature.bind.enable", false)

        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, userId)

        assertTrue(context.players.kicked.isEmpty(), "绑定功能关闭时不应踢人")
        assertEquals(1, database.bindings.size, "绑定功能关闭时不应删数据")
        assertTrue(
            context.logs.any { it.startsWith("DEBUG") && it.contains("已关闭") },
            "跳过必须留痕，否则服主会以为解绑坏了（实际日志：${context.logs}）"
        )
    }

    @Test
    fun `unknown source group is warned about only once per group`() {
        val groupId = 9104L
        database.seed("5004", "Herobrine", null)
        database.seed("5005", "Notch", null)
        val context = FakeContext()

        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, 5004L)
        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, 5005L)

        assertEquals(
            1,
            context.logs.count { it.startsWith("WARN") && it.contains("已保留") },
            "同一群反复出现同类脏数据只该告警一次，否则每次退群都刷屏（实际日志：${context.logs}）"
        )
        assertEquals(2, database.bindings.size, "来源群未知的绑定任何情况下都不自动删除")
    }

    @Test
    fun `a failing kick still unbinds and is reported as a warning`() {
        val groupId = 9105L
        val userId = 5006L
        database.seed(userId.toString(), "Steve", groupId.toString())
        val context = FakeContext()
        context.players.failKick = true

        BindUserDecreaseCommand(BotCommandService(context, FakeBot())).execute("", groupId, userId)

        assertTrue(database.bindings.isEmpty(), "踢人失败不该阻止解绑：玩家已经不在群里了")
        assertTrue(
            context.logs.any { it.startsWith("WARN") && it.contains("踢出失败") },
            "踢人失败必须告警，不能静默（实际日志：${context.logs}）"
        )
    }

    private class FakeConfig : PlatformConfig {
        private val values = mutableMapOf<String, Any>("feature.bind.enable" to true)

        fun set(key: String, value: Any) {
            values[key] = value
        }

        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")

        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class FakePlayers : PlatformPlayerService {
        val kicked = mutableListOf<String>()
        var failKick = false

        override fun kickPlayer(name: String) {
            if (failKick) throw IllegalStateException("假装踢人失败")
            kicked += name
        }

        override fun listPlayers(): List<String> = emptyList()
        override fun listPlayerString(): String = ""
    }

    private class FakeContext : PlatformContext {
        private val fakeConfig = FakeConfig()
        val players = FakePlayers()
        val logs = mutableListOf<String>()

        fun set(key: String, value: Any) = fakeConfig.set(key, value)

        override val config: PlatformConfig get() = fakeConfig
        override val datasource: PlatformConfig get() = fakeConfig
        override val dataDirectory: Path get() = TODO("测试未使用")
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = players
        override val platformType: PlatformType get() = TODO("测试未使用")

        override fun log(level: LogLevel, message: String) {
            logs += "$level $message"
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class FakeBot : IBot {
        override var handler: BotHandler? = null

        override val metadata: BotAdapterMetadata
            get() = BotAdapterMetadata(type = "fake", displayName = "Fake")

        override fun start(): IBot = this
        override fun shutdown() = Unit
        override fun getGroupName(groupId: Long): String = "group-$groupId"
        override fun checkUserInGroup(userId: Long, groupId: Long): Boolean = true
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

    private class InMemoryDatabase : IDatabase {
        val bindings = mutableListOf<Binding>()
        private var nextId = 1

        fun seed(user: String, name: String, group: String?): Binding {
            val binding = Binding(nextId++, user, name, null, Instant.fromEpochMilliseconds(0), group)
            bindings += binding
            return binding
        }

        override fun initialize() = Unit
        override fun close() = Unit
        override fun getConnection(): DataSource = TODO("测试未使用")

        override fun getByUser(user: String): List<Binding> = bindings.filter { it.userId == user }
        override fun getByName(name: String): Binding? =
            bindings.firstOrNull { it.playerName.equals(name, ignoreCase = true) }

        override fun getById(id: Int): Binding? = bindings.firstOrNull { it.id == id }

        override fun addBind(user: String, name: String) {
            seed(user, name, null)
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
