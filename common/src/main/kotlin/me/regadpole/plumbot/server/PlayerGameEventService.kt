package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.platform.PlatformCapability
import me.regadpole.plumbot.platform.PlatformContext

class PlayerGameEventService(private val context: PlatformContext) {

    private val config get() = context.config
    private val sender = ServerMessageSender(context)

    fun notifyJoin(playerName: String) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        val rendered = Messages.joinProxy.replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "joinAndLeave", "pic"))
    }

    fun notifyLeave(playerName: String, serverName: String) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        val rendered = Messages.leaveProxy
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "joinAndLeave", "pic"))
    }

    fun notifyDeath(playerName: String, serverName: String, deathMessage: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_DEATH_BROADCAST)) return
        if (!config.getBoolean("feature", "death", "enable")) return

        val rendered = Messages.death
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%death_message%", deathMessage)
        sender.broadcast(rendered, config.getBoolean("feature", "death", "pic"))
    }

    fun notifyAdvancement(playerName: String, serverName: String, advancementTitle: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST)) return
        if (!config.getBoolean("feature", "advancement", "enable")) return

        val rendered = Messages.advancement
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%advancement_title%", advancementTitle)
        sender.broadcast(rendered, config.getBoolean("feature", "advancement", "pic"))
    }
}
