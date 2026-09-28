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
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.utils.TextToImg
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class MiraiMCAdapter(
    override val context: PlatformContext,
    override val metadata: BotAdapterMetadata
) : AbstractBotAdapter() {

    val botId = context.config.getLong("bot", "miraimc", "botId")
    private lateinit var bot: MiraiBot
    private val isRunning = AtomicBoolean(false)

    override fun start(): IBot {
        super.start()
        return this
    }

    override fun doStart() {
        if (!isRunning.compareAndSet(false, true)) return
        bot = try {
            MiraiBot.getBot(botId)
        } catch (e: Exception) {
            isRunning.set(false)
            throw IllegalStateException("MiraiMC bot $botId is not logged in or does not exist", e)
        }
    }

    override fun doShutdown() {
        isRunning.set(false)
    }

    override fun sendGroupMsg(targetId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            bot.getGroup(targetId).sendMessage(message)
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送群消息失败 ($targetId): ${e.message}")
        }
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            bot.getFriend(targetId).sendMessage(message)
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送私聊消息失败 ($targetId): ${e.message}")
        }
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            val group = bot.getGroup(targetId)
            val tempFile = TextToImg.toFile(message)
            try {
                val imageId = group.uploadImage(tempFile)
                group.sendMessageMirai("[mirai:image:$imageId]")
            } finally {
                runCatching { tempFile.delete() }
            }
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送群图片消息失败 ($targetId): ${e.message}")
        }
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            val friend = bot.getFriend(targetId)
            val tempFile = TextToImg.toFile(message)
            try {
                val imageId = friend.uploadImage(tempFile)
                friend.sendMessageMirai("[mirai:image:$imageId]")
            } finally {
                runCatching { tempFile.delete() }
            }
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送私聊图片消息失败 ($targetId): ${e.message}")
        }
    }

    override fun sendGroupImage(targetId: Long, imageFile: File) {
        if (!isRunning.get()) return
        runCatching {
            val group = bot.getGroup(targetId)
            val imageId = group.uploadImage(imageFile)
            group.sendMessageMirai("[mirai:image:$imageId]")
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 上传并发送群图片失败 ($targetId): ${e.message}")
        }
    }

    override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            val group = bot.getGroup(targetId)
            group.sendMessageMirai("[mirai:at:$userId] $message")
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送群 @ 消息失败 ($targetId): ${e.message}")
        }
    }

    override fun sendGroupMsgAtAll(targetId: Long, message: String) {
        if (!isRunning.get()) return
        runCatching {
            val group = bot.getGroup(targetId)
            group.sendMessageMirai("[mirai:at:all] $message")
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 发送群 @全体 消息失败 ($targetId): ${e.message}")
        }
    }

    override fun preloadGroupCaches() {
        if (!isRunning.get()) return
        runCatching {
            context.config.getLongList("groups").forEach { groupId ->
                bot.getGroup(groupId).members.forEach {
                    primeGroupMember(
                        groupId,
                        MemberInfo(it.id, it.nick, it.nameCard, permissionToRole(it.permission))
                    )
                }
            }
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 预热群成员缓存时出现异常: ${e.message}")
        }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        try {
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
