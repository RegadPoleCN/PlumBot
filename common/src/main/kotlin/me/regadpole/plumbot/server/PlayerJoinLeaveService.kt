package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider

class PlayerJoinLeaveService(private val config: YamlConfigurator) {
    fun notifyJoin(playerName: String) {
        if (!config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        config.getLongList("groups").forEach {
            BotProvider.getBot()?.sendMsg(
                true,
                it,
                Messages.joinProxy
                    .replace("%player_name%", playerName),
                config.getBoolean("feature", "joinAndLeave", "pic")
            )
        }
    }

    fun notifyLeave(playerName: String, serverName: String) {
        if (!config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        config.getLongList("groups").forEach {
            BotProvider.getBot()?.sendMsg(
                true,
                it,
                Messages.leaveProxy
                    .replace("%player_name%", playerName)
                    .replace("%server%", serverName),
                config.getBoolean("feature", "joinAndLeave", "pic")
            )
        }
    }
}
