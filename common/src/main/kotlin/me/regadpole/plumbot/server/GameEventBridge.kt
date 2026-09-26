package me.regadpole.plumbot.server

import me.regadpole.plumbot.bot.command.MessageModeResolver
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.filter.FilterManagerHolder
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.utils.stripMinecraftFormatting

/**
 * 负责统一桥接游戏内发生的所有事件（玩家聊天、进服、离服、遇难、成就）至 QQ 群。
 */
class GameEventBridge(
    private val context: PlatformContext,
    private val sender: ServerMessageSender = ServerMessageSender(context)
) {
    private val config get() = context.config

    fun onChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return
        if (!config.getBoolean("feature", "message", "to_group")) return

        val cleanMessage = rawMessage.stripMinecraftFormatting()

        val filterResult = FilterManagerHolder.manager?.process(cleanMessage)
        if (filterResult?.isBlocked == true) return
        val messageToSend = filterResult?.sanitizedText ?: cleanMessage

        val mode = config.getInteger("feature", "message", "mode")
        val prefix = config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(messageToSend, mode, prefix) ?: return

        val rendered = Messages.server2ob
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%message%", resolved)
        sender.broadcast(rendered, config.getBoolean("feature", "message", "pic"))
    }

    fun onJoin(playerName: String) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        val rendered = Messages.joinProxy.replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "joinAndLeave", "pic"))
    }

    fun onLeave(playerName: String, serverName: String) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        val rendered = Messages.leaveProxy
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "joinAndLeave", "pic"))
    }

    fun onDeath(playerName: String, serverName: String, deathMessage: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_DEATH_BROADCAST)) return
        if (!config.getBoolean("feature", "death", "enable")) return

        val rendered = Messages.death
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%death_message%", deathMessage)
        sender.broadcast(rendered, config.getBoolean("feature", "death", "pic"))
    }

    fun onAdvancement(playerName: String, serverName: String, advancementTitle: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST)) return
        if (!config.getBoolean("feature", "advancement", "enable")) return

        val rendered = Messages.advancement
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%advancement_title%", advancementTitle)
        sender.broadcast(rendered, config.getBoolean("feature", "advancement", "pic"))
    }
}
