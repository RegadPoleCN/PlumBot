package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.MessageModeResolver
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.stripMinecraftFormatting

class ServerChatService(
    private val context: PlatformContext,
    private val sender: ServerMessageSender = ServerMessageSender(context)
) {

    private val config get() = context.config

    fun handleChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return
        val toGroup = config.getBoolean("feature", "message", "to_group")
        if (!toGroup) return

        val cleanMessage = stripMinecraftFormatting(rawMessage)

        val filterResult = me.regadpole.plumbot.filter.FilterManagerHolder.manager?.process(cleanMessage)
        if (filterResult?.isBlocked == true) {
            return
        }
        val messageToSend = filterResult?.sanitizedText ?: cleanMessage

        val mode = config.getInteger("feature", "message", "mode")
        val prefix = config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(messageToSend, mode, prefix) ?: return
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