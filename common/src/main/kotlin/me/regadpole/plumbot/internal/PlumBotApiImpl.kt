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

package me.regadpole.plumbot.internal

import kotlinx.coroutines.Dispatchers
import me.regadpole.plumbot.internal.PlayerNameRules
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

    override val extensionRegistry: BotExtensionRegistry by lazy {
        DefaultBotExtensionRegistry(BotProvider)
    }

    override val boundGroupIds: Set<Long>
        get() = plugin.config.getLongList("groups").toSet()

    override val currentBotId: String
        get() = BotProvider.getBot()?.metadata?.type ?: "unknown"

    /** @deprecated 见 `PlumBotAPI.isBotConnected`（D8 弃用周期）。 */
    @Deprecated("语义含糊，请改用状态查询 API")
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

    override suspend fun sendGroupImage(groupId: Long, imageBytes: ByteArray) = withContext(Dispatchers.IO) {
        if (imageBytes.isEmpty()) {
            throw IllegalArgumentException("图片字节为空")
        }
        val bot = BotProvider.getBot() ?: throw BotNotReadyException()
        try {
            bot.sendGroupImage(groupId, imageBytes)
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
        // 按 player_uuid 查询：此前把 UUID 当 user_id 再当 player_name 查，两个分支都不可能命中，
        // 结果对外恒为 null（UUID 反查形同未实现）。
        DatabaseProvider.getDatabase()
            ?.getByPlayerUuid(uuid.toString())
            ?.userId
            ?.toLongOrNull()
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
        if (!PlayerNameRules.isValid(cleanName)) return@withContext false
        if (isPlayerWhitelisted(cleanName)) return@withContext false

        database.addBind(userId, cleanName)
        if (uuid != null) {
            database.setUUID(cleanName, uuid)
        }
        true
    }

    override suspend fun removeBinding(playerName: String): Boolean = withContext(Dispatchers.IO) {
        val database = DatabaseProvider.getDatabase() ?: return@withContext false
        // N12：**匹配与踢人统一走规范化名**。
        // DB 侧一律存 lowercase（`AbstractBindingDatabase.normalize`），而踢人此前用原始大小写，
        // 造成"库里是 steve、踢人时传 Steve"这类不对称 —— 在三端宿主上是否都能踢掉并无保证。
        // 规范化后两侧同名；对外展示若需要原始大小写，应由调用方或宿主在线玩家名提供。
        val normalized = playerName.trim().lowercase()
        if (!isPlayerWhitelisted(normalized)) return@withContext false

        database.removeBind(normalized)
        plugin.kickPlayer(normalized)
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
