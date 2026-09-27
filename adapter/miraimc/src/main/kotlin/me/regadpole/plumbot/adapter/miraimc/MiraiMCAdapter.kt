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

package me.regadpole.plumbot.adapter.miraimc

import me.dreamvoid.miraimc.api.MiraiBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.api.platform.PlatformContext
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
        val tempFile = TextToImg.toFile(message)
        try {
            val imageId = group.uploadImage(tempFile)
            group.sendMessageMirai("[mirai:image:$imageId]")
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val friend = bot.getFriend(targetId)
        val tempFile = TextToImg.toFile(message)
        try {
            val imageId = friend.uploadImage(tempFile)
            friend.sendMessageMirai("[mirai:image:$imageId]")
        } finally {
            runCatching { tempFile.delete() }
        }
    }

    override fun sendGroupImage(targetId: Long, imageFile: java.io.File) {
        val group = bot.getGroup(targetId)
        val imageId = group.uploadImage(imageFile)
        group.sendMessageMirai("[mirai:image:$imageId]")
    }

    override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) {
        val group = bot.getGroup(targetId)
        group.sendMessageMirai("[mirai:at:$userId] $message")
    }

    override fun sendGroupMsgAtAll(targetId: Long, message: String) {
        val group = bot.getGroup(targetId)
        group.sendMessageMirai("[mirai:at:all] $message")
    }

    override fun preloadGroupCaches() {
        context.config.getLongList("groups").forEach { groupId ->
            bot.getGroup(groupId).members.forEach {
                primeGroupMember(
                    groupId,
                    MemberInfo(it.id, it.nick, it.nameCard, permissionToRole(it.permission))
                )
            }
        }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        try {
            // MiraiMC 协议层（Bot.getGroup(name)）为同步调用，无异步回调。
            // 直接 complete 与 OneBot 异步回调路径在外部观察者侧保持一致语义。
            future.complete(bot.getGroup(groupId).name)
        } catch (e: Exception) {
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
