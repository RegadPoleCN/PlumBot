package me.regadpole.plumbot.adapter.miraimc

import me.dreamvoid.miraimc.api.MiraiBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.MemberInfo
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.TextToImg
import java.util.concurrent.CompletableFuture

class MiraiMCAdapter(
    override val context: PlatformContext,
    override val metadata: BotAdapterMetadata
) : AbstractBotAdapter() {

    val botId = context.config.getLong("bot", "miraimc", "botId")
    private lateinit var bot: MiraiBot

    override fun start(): IBot {
        super.start()
        return this
    }

    override fun doStart() {
        bot = try {
            MiraiBot.getBot(botId)
        } catch (e: Exception) {
            throw IllegalStateException("MiraiMC bot $botId is not logged in or does not exist", e)
        }
    }

    override fun doShutdown() {
        // MiraiMC bot lifecycle由外部插件管理，Adapter无需额外处理。
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

    override fun preloadGroupCaches() {
        context.config.getLongList("groups").forEach { groupId ->
            bot.getGroup(groupId).members.forEach {
                groupMemberCache.put(groupId, it.id,
                    MemberInfo(it.id, it.nick, it.nameCard, permissionToRole(it.permission)))
            }
        }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        try {
            future.complete(bot.getGroup(groupId).name)
        } catch (e: Throwable) {
            future.completeExceptionally(e)
        }
        return future
    }

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
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
