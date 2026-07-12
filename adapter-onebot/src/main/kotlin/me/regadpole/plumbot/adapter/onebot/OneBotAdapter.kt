package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotImpl
import me.regadpole.plumbot.bot.DefaultBotCache
import me.regadpole.plumbot.bot.DefaultGroupMemberCache
import me.regadpole.plumbot.bot.MemberInfo
import me.regadpole.plumbot.listener.BotHandler
import me.regadpole.plumbot.listener.DefaultBotHandler
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.TextToImg
import top.alazeprt.aonebot.action.GetGroupInfo
import top.alazeprt.aonebot.action.GetGroupMemberInfo
import top.alazeprt.aonebot.action.GetGroupMemberList
import top.alazeprt.aonebot.action.SendGroupMessage
import top.alazeprt.aonebot.action.SendPrivateMessage
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import top.alazeprt.aonebot.result.Group
import java.util.concurrent.CompletableFuture

class OneBotAdapter(
    val context: PlatformContext,
    val client: WebsocketBotClient,
    override val metadata: BotAdapterMetadata
): BotImpl {

    override var handler: BotHandler? = null
    val groupMemberCache = DefaultGroupMemberCache(::fetchMember)
    private val groupNameCache = DefaultBotCache<Long, String>(::loadGroupName)

    override fun start(): BotImpl {
        handler = DefaultBotHandler(context, this)
        client.connect()
        client.registerEvent(OneBotListener(this))

        if (context.config.getBoolean("feature", "load", "enable")) context.config.getLongList("groups").forEach {
            sendMsg(true, it,
                Messages.load
                , context.config.getBoolean("feature", "load", "pic"))
        }

        preloadCache()

        return this
    }

    override fun shutdown() {
        if (context.config.getBoolean("feature", "load", "enable")) context.config.getLongList("groups").forEach {
            if (context.config.getBoolean("feature", "load", "pic")) sendGroupPicWithText(it, Messages.unload)
            else sendGroupMsg(it, Messages.unload)
        }
        handler = null
        groupMemberCache.invalidateAll()
        groupNameCache.invalidateAll()
        client.disconnect()
    }

    override fun getGroupName(groupId: Long): String {
        val future = groupNameCache.get(groupId)
        return DefaultGroupMemberCache.await(future, groupId.toString())
    }

    override fun checkUserInGroup(userId: Long, groupId: Long): Boolean {
        return DefaultGroupMemberCache.await(
            groupMemberCache.get(groupId, userId).thenApply { it != null },
            false
        )
    }

    override fun sendGroupMsg(targetId: Long, message: String) {
        client.action(SendGroupMessage(targetId, message, false))
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        client.action(SendPrivateMessage(targetId, message, false))
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val msg = TextToImg.toImgCQCode(message)
        client.action(SendGroupMessage(targetId, msg, false))
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val msg = TextToImg.toImgCQCode(message)
        client.action(SendPrivateMessage(targetId, msg, false))
    }

    override fun getGroupUserName(groupId: Long, targetId: Long): String {
        val fallback = MemberInfo(targetId, targetId.toString(), targetId.toString())
        val info = DefaultGroupMemberCache.await(
            groupMemberCache.get(groupId, targetId).thenApply { it ?: fallback },
            fallback
        )
        return info.name.takeIf { it.isNotBlank() } ?: targetId.toString()
    }

    override fun getGroupUserCard(groupId: Long, targetId: Long): String {
        val fallback = MemberInfo(targetId, targetId.toString(), targetId.toString())
        val info = DefaultGroupMemberCache.await(
            groupMemberCache.get(groupId, targetId).thenApply { it ?: fallback },
            fallback
        )
        return info.card.takeIf { it.isNotBlank() }
            ?: info.name.takeIf { it.isNotBlank() }
            ?: targetId.toString()
    }

    /**
     * 刷新指定群的缓存；基于 CompletableFuture 实现。
     */
    fun refreshCache(groupId: Long): CompletableFuture<Unit> {
        groupNameCache.invalidate(groupId)
        return groupMemberCache.refresh(groupId)
    }

    private fun preloadCache(){
        context.config.getLongList("groups").forEach { groupId ->
            client.action(GetGroupMemberList(groupId)) { users ->
                users.forEach {
                    val role = try {
                        it.role?.toString()
                    } catch (_: Exception) {
                        null
                    } ?: "member"
                    groupMemberCache.put(groupId, it.member.userId,
                        MemberInfo(it.member.userId, it.member.nickname, it.card, role))
                }
            }
        }
    }

    private fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        client.action(GetGroupInfo(groupId)) { group: Group ->
            try {
                future.complete(group.groupName)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        val future = CompletableFuture<MemberInfo?>()
        client.action(GetGroupMemberInfo(groupId, userId)) { result ->
            try {
                val role = result.role?.toString() ?: "member"
                future.complete(MemberInfo(result.member.userId, result.member.nickname, result.card, role))
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }
}
