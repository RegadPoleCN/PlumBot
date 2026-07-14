package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.command.MessageModeResolver

class ServerChatService(private val config: YamlConfigurator) {
    fun handleChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return

        val message = rawMessage.replace(Regex("&([0-9a-fklmnor])")) { matchResult ->
            "§" + matchResult.groupValues[1]
        }.replace(Regex("#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})"), "")

        val mode = config.getInteger("feature", "message", "mode")
        val prefix = config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(message, mode, prefix) ?: return
        sendServerMessage(serverName, playerName, resolved)
    }

    private fun sendServerMessage(serverName: String, playerName: String, message: String) {
        val rendered = Messages.server2ob
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%message%", message)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "message", "pic")
        )
    }
}