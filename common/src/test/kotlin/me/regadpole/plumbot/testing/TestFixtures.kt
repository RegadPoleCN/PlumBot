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

package me.regadpole.plumbot.testing

import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.bot.AbstractBotAdapter
import kotlinx.datetime.Clock
import net.kyori.adventure.text.Component
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

/**
 * 共享测试夹具 —— **U03 的前置**。
 *
 * 为什么集中：此前每个测试各自复制一份 `FakeContext`/`FakeBot`（各约 60 行，且都是 `private`），
 * 于是"给某个改动补测试"的成本高到会被跳过 —— 这正是 A06–A10、A12–A14、A17 长期只有实现没有验收的原因。
 * 把夹具抽到一处后，新增一个回归用例只需十几行。
 *
 * 约定：**只提供替身，不隐藏断言** —— 每个夹具都把观察点（日志、发送内容、调用次数）暴露为公开属性。
 */
class FakePlatformConfig(initial: Map<String, Any> = emptyMap()) : PlatformConfig {

    val values: MutableMap<String, Any> = LinkedHashMap(initial)

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

    override fun getStringList(vararg path: String?): List<String?> =
        @Suppress("UNCHECKED_CAST")
        ((values[key(path)] as? List<Any?>) ?: emptyList()).map { it as? String }

    override fun getSubConfig(vararg path: String?): PlatformConfig = this
}

/** 平台上下文替身：可 `reload()`（换新配置实例）、记录日志与对外发送的消息。 */
class FakePlatformContext(initial: Map<String, Any> = emptyMap()) : PlatformContext {

    private var holder = FakePlatformConfig(initial)

    /** 真实临时目录：门户/缓存等组件会读取它，用 `TODO()` 会让无关测试直接抛错。 */
    val tempDir: Path = java.nio.file.Files.createTempDirectory("plumbot-test-ctx").also { it.toFile().deleteOnExit() }

    val logs = mutableListOf<String>()
    val sentMessages = mutableListOf<String>()

    override val config: PlatformConfig get() = holder
    override val datasource: PlatformConfig get() = holder

    fun set(key: String, value: Any) = holder.set(key, value)

    /** 模拟 `/plumbot reload`：**换一个全新的配置实例**（用于验证读取方没有缓存旧引用）。 */
    fun reload() {
        holder = FakePlatformConfig(holder.values.toMap())
    }

    override val dataDirectory: Path get() = tempDir
    override val scheduler: PlatformScheduler = NoopScheduler
    override val playerService: PlatformPlayerService get() = TODO("测试未使用玩家服务")
    override val platformType: PlatformType get() = PlatformType.BUKKIT

    override fun log(level: LogLevel, message: String) {
        logs += "$level $message"
    }

    override fun sendMessage(message: Component) {
        sentMessages += message.toString()
    }

    override fun isPluginAvailable(name: String): Boolean = false
}

/** 不做任何调度的调度器替身（记录调用次数即可断言"是否被调用"）。 */
object NoopScheduler : PlatformScheduler {
    override fun runSync(task: () -> Unit): PlatformTaskHandle {
        task()
        return PlatformTaskHandle { true }
    }

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        task()
        return PlatformTaskHandle { true }
    }

    override fun runLater(delay: kotlin.time.Duration, task: () -> Unit): PlatformTaskHandle {
        return PlatformTaskHandle { true }
    }

    override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit): PlatformTaskHandle {
        return PlatformTaskHandle { true }
    }

    override fun runRepeating(
        initialDelay: kotlin.time.Duration,
        period: kotlin.time.Duration,
        task: () -> Unit,
    ): PlatformTaskHandle {
        return PlatformTaskHandle { true }
    }

    override fun runRepeatingAsync(
        initialDelay: kotlin.time.Duration,
        period: kotlin.time.Duration,
        task: () -> Unit,
    ): PlatformTaskHandle {
        return PlatformTaskHandle { true }
    }
}

/**
 * Bot 替身。
 *
 * 继承 [AbstractBotAdapter]（而不是直接实现 `IBot`）是**有意为之**：生命周期相关的改动
 * （作用域可重建、关闭链、handler 释放）都发生在基类，只有继承它才能真正覆盖那些路径。
 */
open class FakeBot(
    override val context: FakePlatformContext = FakePlatformContext(),
    type: String = "fake",
    capabilities: Set<BotCapability> = setOf(
        BotCapability.GROUP_MESSAGE_RECEIVE,
        BotCapability.GROUP_MESSAGE_SEND,
        BotCapability.GROUP_MEMBER_QUERY,
    ),
) : AbstractBotAdapter() {

    override val metadata: BotAdapterMetadata =
        BotAdapterMetadata(type = type, displayName = "Fake-$type", capabilities = capabilities)

    val sentMessages = mutableListOf<String>()
    var memberInGroup = true
    var doStartCalled = false
    var doShutdownCalled = false

    override fun doStart() {
        doStartCalled = true
    }

    override fun doShutdown() {
        doShutdownCalled = true
    }

    override fun preloadGroupCaches() = Unit

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> =
        CompletableFuture.completedFuture(
            if (memberInGroup) MemberInfo(userId, "user-$userId", "card-$userId") else null
        )

    override fun loadGroupName(groupId: Long): CompletableFuture<String> =
        CompletableFuture.completedFuture("group-$groupId")

    override fun sendGroupMsg(targetId: Long, message: String) {
        sentMessages += message
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        sentMessages += message
    }
}

/** 供 `BotProvider.loadBot(...)` 使用的工厂替身。 */
class FakeBotFactory(private val bot: FakeBot = FakeBot()) : BotFactory {

    override val metadata: BotAdapterMetadata get() = bot.metadata

    override fun create(context: PlatformContext): IBot = bot
}

/**
 * 内存版绑定库替身（真实语义：写小写、按名唯一、UUID 可空）。
 *
 * 与真实实现的关键差异：**不做 SQL 校验**（那也是 A03/A04 用真实 SQLite 测的原因），
 * 这里只用于覆盖业务层分支。
 */
class RecordingDatabase : IDatabase {

    private val rows = mutableListOf<Binding>()
    private var nextId = 1

    override fun initialize() = Unit
    override fun close() = Unit
    override fun getConnection(): DataSource = TODO("测试未使用连接池")

    override fun getByUser(user: String): List<Binding> = rows.filter { it.userId == user.trim() }

    override fun getByName(name: String): Binding? =
        rows.firstOrNull { it.playerName.equals(name.trim(), ignoreCase = true) }

    override fun getById(id: Int): Binding? = rows.firstOrNull { it.id == id }

    override fun getByPlayerUuid(uuid: String): Binding? =
        rows.firstOrNull { it.playerUUID?.equals(uuid.trim(), ignoreCase = true) == true }

    override fun addBind(user: String, name: String) {
        rows += Binding(nextId++, user.trim(), name.trim().lowercase(), null, Clock.System.now())
    }

    override fun addBindIfAbsent(user: String, name: String): Boolean {
        if (getByName(name) != null) return false
        addBind(user, name)
        return true
    }

    override fun removeBindByNum(user: String, id: Int): String? {
        val hit = rows.firstOrNull { it.userId == user.trim() && it.id == id } ?: return null
        rows.remove(hit)
        return hit.playerName
    }

    override fun removeBindByUser(user: String) {
        rows.removeAll { it.userId == user.trim() }
    }

    override fun removeBind(name: String) {
        rows.removeAll { it.playerName.equals(name.trim(), ignoreCase = true) }
    }

    override fun setUUID(name: String, uuid: UUID) {
        val index = rows.indexOfFirst { it.playerName.equals(name.trim(), ignoreCase = true) }
        if (index < 0) return
        val old = rows[index]
        rows[index] = Binding(old.id, old.userId, old.playerName, uuid.toString(), old.bindingTime)
    }
}
