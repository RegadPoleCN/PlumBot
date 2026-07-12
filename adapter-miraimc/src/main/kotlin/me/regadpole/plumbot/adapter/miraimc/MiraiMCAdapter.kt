package me.regadpole.plumbot.adapter.miraimc

import me.dreamvoid.miraimc.api.MiraiBot
import me.regadpole.plumbot.api.bot.IBot
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
import java.util.concurrent.CompletableFuture

class MiraiMCAdapter(
    val context: PlatformContext,
    override val metadata: BotAdapterMetadata
): BotImpl {

    val botId = context.config.getLong("bot", "miraimc", "botId")
    private val bot = MiraiBot.getBot(botId)

    override var handler: BotHandler? = null
    val groupMemberCache = DefaultGroupMemberCache(::fetchMember)
    private val groupNameCache = DefaultBotCache<Long, String>(::loadGroupName)

    override fun start(): IBot {
        handler = DefaultBotHandler(context, this)

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
        bot.getGroup(targetId).sendMessage(message)
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        bot.getFriend(targetId).sendMessage(message)
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val group = bot.getGroup(targetId)
        val imageId = group.uploadImage(TextToImg.toFile(message))
        group.sendMessageMirai("[mirai:image:$imageId]")
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val friend = bot.getFriend(targetId)
        val imageId = friend.uploadImage(TextToImg.toFile(message))
        friend.sendMessageMirai("[mirai:image:$imageId]")
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
            bot.getGroup(groupId).members.forEach {
                groupMemberCache.put(groupId, it.id,
                    MemberInfo(it.id, it.nick, it.nameCard, permissionToRole(it.permission)))
            }
        }
    }

    private fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        try {
            future.complete(bot.getGroup(groupId).name)
        } catch (e: Throwable) {
            future.completeExceptionally(e)
        }
        return future
    }

    private fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        val future = CompletableFuture<MemberInfo?>()
        try {
            val member = bot.getGroup(groupId).getMember(userId)
                ?: throw NoSuchElementException("Member $userId not found in group $groupId")
            future.complete(MemberInfo(member.id, member.nick, member.nameCard, permissionToRole(member.permission)))
        } catch (e: Throwable) {
            future.completeExceptionally(e)
        }
        return future
    }

    private fun permissionToRole(permission: Int): String = when (permission) {
        2 -> "owner"
        1 -> "admin"
        else -> "member"
    }
}
