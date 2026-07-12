package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider

class ServerChatService(private val config: YamlConfigurator) {
    fun handleChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return

        val message = rawMessage.replace(Regex("&([0-9a-fklmnor])")) { matchResult ->
            "§" + matchResult.groupValues[1]
        }.replace(Regex("#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})"), "")

        if (config.getInteger("feature", "message", "mode") == 0) {
            sendServerMessage(serverName, playerName, message)
            return
        } else if (config.getInteger("feature", "message", "mode") == 1) {
            val prefix = config.getString("feature", "message", "prefix")!!
            if (Regex(prefix).matchesAt(rawMessage, 0)) {
                sendServerMessage(serverName, playerName, message.replace(prefix, ""))
                return
            }
        }
    }

    private fun sendServerMessage(serverName: String, playerName: String, message: String) {
        config.getLongList("groups").forEach {
            BotProvider.getBot()?.sendMsg(
                true,
                it,
                Messages.server2ob
                    .replace("%server%", serverName)
                    .replace("%player_name%", playerName)
                    .replace("%message%", message),
                config.getBoolean("feature", "message", "pic")
            )
        }
    }
}
