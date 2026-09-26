package me.regadpole.plumbot.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.exception.BotMessageSendException
import me.regadpole.plumbot.api.exception.BotNotReadyException
import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.api.bot.BotExtensionRegistry
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.bot.DefaultBotExtensionRegistry
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.task.TaskProviderImpl
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

fun createPlumBotApi(plugin: PlumBot): PlumBotAPI = PlumBotApiImpl(plugin)

internal class PlumBotApiImpl(private val plugin: PlumBot) : PlumBotAPI {

    private val playerNameRegex = Regex("""^[a-zA-Z0-9_\.*]{3,16}$""")

    override val extensionRegistry: BotExtensionRegistry by lazy {
        DefaultBotExtensionRegistry(BotProvider)
    }

    override val boundGroupIds: Set<Long>
        get() = plugin.config.getLongList("groups").toSet()

    override val currentBotId: String
        get() = BotProvider.getBot()?.metadata?.type ?: "unknown"

    override val isBotConnected: Boolean
        get() = BotProvider.getBot() != null

    // ================= 消息发送 =================

    override suspend fun sendGroupMessage(groupId: Long, message: String, asImage: Boolean) = withContext(Dispatchers.IO) {
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        try {
            bot.sendMsg(isGroup = true, targetId = groupId, message = message, isPic = asImage)
        } catch (e: Throwable) {
            throw BotMessageSendException("发送群消息失败 (群: $groupId)", e)
        }
    }

    override suspend fun sendGroupMessageAt(groupId: Long, userId: Long, message: String) = withContext(Dispatchers.IO) {
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        try {
            bot.sendGroupMsgAt(groupId, userId, message)
        } catch (e: Throwable) {
            throw BotMessageSendException("发送群 At 消息失败 (群: $groupId, 用户: $userId)", e)
        }
    }

    override suspend fun sendGroupMessageAtAll(groupId: Long, message: String) = withContext(Dispatchers.IO) {
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        try {
            bot.sendGroupMsgAtAll(groupId, message)
        } catch (e: Throwable) {
            throw BotMessageSendException("发送群 At全体 消息失败 (群: $groupId)", e)
        }
    }

    override suspend fun sendGroupImage(groupId: Long, imageFile: File) = withContext(Dispatchers.IO) {
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        if (!imageFile.exists() || !imageFile.isFile) {
            throw IllegalArgumentException("指定图片文件不存在: ${imageFile.absolutePath}")
        }
        try {
            bot.sendGroupImage(groupId, imageFile)
        } catch (e: Throwable) {
            throw BotMessageSendException("发送群图片失败 (群: $groupId)", e)
        }
    }

    override suspend fun broadcastToAllGroups(message: String, asImage: Boolean) {
        for (groupId in boundGroupIds) {
            runCatching { sendGroupMessage(groupId, message, asImage) }
        }
    }

    override suspend fun sendUserMessage(userId: Long, message: String, asImage: Boolean) = withContext(Dispatchers.IO) {
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        try {
            bot.sendMsg(isGroup = false, targetId = userId, message = message, isPic = asImage)
        } catch (e: Throwable) {
            throw BotMessageSendException("发送私聊消息失败 (用户: $userId)", e)
        }
    }

    // ================= 强制绑定宿主生命周期的事件注册 =================

    override fun subscribeGroupMessage(plugin: Plugin, handler: (GroupMessageEvent) -> Unit): ListenerHandle {
        val handle = BotEventDispatcher.registerGroupMessageHandler(handler)
        BotEventDispatcher.bindPluginLifecycle(plugin, handle)
        return handle
    }

    override fun subscribeGroupMemberDecrease(plugin: Plugin, handler: (GroupMemberDecreaseEvent) -> Unit): ListenerHandle {
        val handle = BotEventDispatcher.registerMemberDecreaseHandler(handler)
        BotEventDispatcher.bindPluginLifecycle(plugin, handle)
        return handle
    }

    // ================= 账号绑定与白名单 =================

    override suspend fun getBindingUser(playerName: String): Long? = withContext(Dispatchers.IO) {
        DatabaseProvider.getBindByName(playerName)?.toLongOrNull()
    }

    override suspend fun getBindingUser(uuid: UUID): Long? = withContext(Dispatchers.IO) {
        DatabaseProvider.getBindByUser(uuid.toString()).values.firstOrNull()?.toLong()
            ?: DatabaseProvider.getBindByName(uuid.toString())?.toLongOrNull()
    }

    override suspend fun getBindingAccounts(userId: Long): Map<String, UUID?> = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase() ?: return@withContext emptyMap()
        database.getByUser(userId.toString()).associate { binding ->
            val uuid = binding.playerUUID?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            binding.playerName to uuid
        }
    }

    override suspend fun isPlayerWhitelisted(playerName: String): Boolean = withContext(Dispatchers.IO) {
        DatabaseProvider.getBindByName(playerName) != null
    }

    override suspend fun addBinding(userId: Long, playerName: String, uuid: UUID?): Boolean = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase() ?: return@withContext false
        val cleanName = playerName.trim()
        if (!playerNameRegex.matches(cleanName)) return@withContext false
        if (isPlayerWhitelisted(cleanName)) return@withContext false

        database.addBind(userId, cleanName)
        if (uuid != null) {
            database.setUUID(cleanName, uuid)
        }
        true
    }

    override suspend fun removeBinding(playerName: String): Boolean = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase() ?: return@withContext false
        val cleanName = playerName.trim()
        if (!isPlayerWhitelisted(cleanName)) return@withContext false

        database.removeBind(cleanName)
        plugin.kickPlayer(cleanName)
        true
    }

    // ================= 权限与群成员资料 =================

    override fun isBotAdmin(userId: Long): Boolean =
        plugin.config.getLongList("admins").contains(userId)

    override suspend fun getGroupMemberInfo(groupId: Long, userId: Long): MemberInfo? {
        val adapter = BotProvider.getBot() as? AbstractBotAdapter ?: return null
        return adapter.groupMemberCache.getAsync(groupId, userId)
    }

    override suspend fun isGroupAdmin(groupId: Long, userId: Long): Boolean {
        val info = getGroupMemberInfo(groupId, userId) ?: return false
        return info.role.equals("owner", ignoreCase = true) || info.role.equals("admin", ignoreCase = true)
    }

    // ================= Java 异步互操作实现 =================

    override fun sendGroupMessageAsync(groupId: Long, message: String): CompletableFuture<Unit> =
        TaskProviderImpl.launchFuture { sendGroupMessage(groupId, message) }

    override fun broadcastToAllGroupsAsync(message: String): CompletableFuture<Unit> =
        TaskProviderImpl.launchFuture { broadcastToAllGroups(message) }

    override fun getBindingUserAsync(playerName: String): CompletableFuture<Long?> =
        TaskProviderImpl.launchFuture { getBindingUser(playerName) }

    override fun getBindingAccountsAsync(userId: Long): CompletableFuture<Map<String, UUID?>> =
        TaskProviderImpl.launchFuture { getBindingAccounts(userId) }

    override fun isPlayerWhitelistedAsync(playerName: String): CompletableFuture<Boolean> =
        TaskProviderImpl.launchFuture { isPlayerWhitelisted(playerName) }
}
