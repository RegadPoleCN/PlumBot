// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.adapter.miraimc

import me.dreamvoid.miraimc.api.MiraiBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MiraiMC 直连适配器：消息经 MiraiBot 会话发出，不走 OneBot HTTP。
 *
 * 在线状态由 [isRunning] 单点表达；离线时发送接口静默丢弃（图片返回 false），
 * 因此调用方不能只凭"没有异常"判断送达。
 */
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
        // 取到 bot 即视为在线；MiraiMC 没有重连循环，掉线只能靠后续调用失败暴露。
        markOnline()
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

    // 发图返回布尔：连接未就绪与上传失败都必须以 false 上报 —— 只记日志会让调用方
    // 误以为已发出；WARN 保留群号与原因，便于定位。
    override fun sendGroupImage(targetId: Long, imageFile: File): Boolean {
        if (!isRunning.get()) return false
        return runCatching {
            val group = bot.getGroup(targetId)
            val imageId = group.uploadImage(imageFile)
            group.sendMessageMirai("[mirai:image:$imageId]")
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 上传并发送群图片失败 ($targetId): ${e.message}")
        }.isSuccess
    }

    override fun sendUserImage(targetId: Long, imageFile: File): Boolean {
        if (!isRunning.get()) return false
        return runCatching {
            val friend = bot.getFriend(targetId)
            val imageId = friend.uploadImage(imageFile)
            friend.sendMessageMirai("[mirai:image:$imageId]")
        }.onFailure { e ->
            context.log(LogLevel.WARN, "[MiraiMC] 上传并发送私聊图片失败 ($targetId): ${e.message}")
        }.isSuccess
    }

    override fun sendUserImage(targetId: Long, imageBytes: ByteArray): Boolean {
        if (!isRunning.get()) return false
        val temp = File.createTempFile("plumbot_mirai_", ".png")
        try {
            temp.writeBytes(imageBytes)
            return sendUserImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    override fun sendGroupImage(targetId: Long, imageBytes: ByteArray): Boolean {
        if (!isRunning.get()) return false
        val temp = File.createTempFile("plumbot_mirai_", ".png")
        try {
            temp.writeBytes(imageBytes)
            return sendGroupImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
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
            if (member == null) {
                // 目标不在群内是正常数据状态：以 null 表达，避免缓存层当作加载失败刷 WARN。
                future.complete(null)
            } else {
                future.complete(MemberInfo(member.id, member.nick, member.nameCard, permissionToRole(member.permission)))
            }
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
