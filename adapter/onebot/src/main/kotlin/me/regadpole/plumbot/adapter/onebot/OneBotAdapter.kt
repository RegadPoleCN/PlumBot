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
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(message))
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(message))
        client.action(SendPrivateMessageSegments(targetId, segments))
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val cleanText = stripColorCodes(message)
        if (!TextToImg.isSupported) {
            sendGroupMsg(targetId, cleanText)
            return
        }
        val segments = try {
            val bytes = TextToImg.toByteArray(message)
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        } catch (e: Exception) {
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(cleanText))
        }
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val cleanText = stripColorCodes(message)
        if (!TextToImg.isSupported) {
            sendUserMsg(targetId, cleanText)
            return
        }
        val segments = try {
            val bytes = TextToImg.toByteArray(message)
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        } catch (e: Exception) {
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(cleanText))
        }
        client.action(SendPrivateMessageSegments(targetId, segments))
    }

    override fun sendGroupImage(targetId: Long, imageFile: java.io.File) {
        val bytes = imageFile.readBytes()
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(
            OneBotSegmentBuilder.at(userId),
            OneBotSegmentBuilder.text(" $message")
        )
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendGroupMsgAtAll(targetId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(
            OneBotSegmentBuilder.atAll(),
            OneBotSegmentBuilder.text(" $message")
        )
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun preloadGroupCaches() {
        context.config.getLongList("groups").forEach { groupId ->
            client.action(GetGroupMemberList(groupId)) { users ->
                users.forEach {
                    primeGroupMember(
                        groupId,
                        MemberInfo(it.member.userId, it.member.nickname, it.card, extractRole(it.role))
                    )
                }
            }
        }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        client.action(GetGroupInfo(groupId)) { group: Group ->
            try {
                future.complete(group.groupName)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        val future = CompletableFuture<MemberInfo?>()
        client.action(GetGroupMemberInfo(groupId, userId)) { result ->
            try {
                future.complete(
                    MemberInfo(
                        result.member.userId,
                        result.member.nickname,
                        result.card,
                        extractRole(result.role)
                    )
                )
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * 提取 OneBot 角色字段，兼容不同版本 API 返回的 role 类型（String 或枚举）。
     * 解析失败或缺失时统一回落到 "member"，与 MiraiMCAdapter 行为一致。
     */
    private fun extractRole(role: Any?): String {
        return try {
            role?.toString()
        } catch (_: Exception) {
            null
        } ?: "member"
    }
}
