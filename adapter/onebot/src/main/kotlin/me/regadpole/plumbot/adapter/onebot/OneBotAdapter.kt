package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotImpl
import me.regadpole.plumbot.bot.MemberInfo
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
    override val context: PlatformContext,
    val client: WebsocketBotClient,
    override val metadata: BotAdapterMetadata
) : AbstractBotAdapter() {

    override fun start(): BotImpl {
        super.start()
        return this
    }

    override fun doStart() {
        client.connect()
        client.registerEvent(OneBotListener(this))
    }

    override fun doShutdown() {
        client.disconnect()
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

    override fun preloadGroupCaches() {
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

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
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

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
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
