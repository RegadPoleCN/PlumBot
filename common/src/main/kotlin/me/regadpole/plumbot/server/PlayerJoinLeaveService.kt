package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator

object PlayerJoinLeaveService {
    fun notifyJoin(playerName: String, config: YamlConfigurator) {
        if (!config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        val rendered = Messages.joinProxy
            .replace("%player_name%", playerName)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "joinAndLeave", "pic")
        )
    }

    fun notifyLeave(playerName: String, serverName: String, config: YamlConfigurator) {
        if (!config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        val rendered = Messages.leaveProxy
            .replace("%player_name%", playerName)
            .replace("%server%", serverName)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "joinAndLeave", "pic")
        )
    }
}
