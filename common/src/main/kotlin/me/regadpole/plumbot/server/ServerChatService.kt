package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.MessageModeResolver
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.stripMinecraftFormatting

class ServerChatService(private val context: PlatformContext) {

    private val config get() = context.config
    private val sender = ServerMessageSender(context)

    fun handleChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return
        val toGroup = config.getBoolean("feature", "message", "to_group")
        if (!toGroup) return

        val cleanMessage = stripMinecraftFormatting(rawMessage)

        val mode = config.getInteger("feature", "message", "mode")
        val prefix = config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(cleanMessage, mode, prefix) ?: return
        sendServerMessage(serverName, playerName, resolved)
    }

    private fun sendServerMessage(serverName: String, playerName: String, message: String) {
        val rendered = Messages.server2ob
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%message%", message)
        sender.broadcast(
            rendered,
            config.getBoolean("feature", "message", "pic")
        )
    }
}