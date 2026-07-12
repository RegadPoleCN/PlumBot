package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.utils.getComponentFromMiniMsg

class MessageForwardService(private val service: BotCommandService) {
    fun forward(message: String, groupId: Long, userId: Long) {
        if (!service.isFeatureEnabled("message")) return

        if (service.config.getInteger("feature", "message", "mode") == 0) {
            sendOb2ServerMessage(message, groupId, userId)
            return
        } else if (service.config.getInteger("feature", "message", "mode") == 1) {
            val prefix = service.config.getString("feature", "message", "prefix")!!
            if (Regex(prefix).matchesAt(message, 0)) {
                sendOb2ServerMessage(message.replace(prefix, ""), groupId, userId)
                return
            }
        }
    }

    private fun sendOb2ServerMessage(message: String, groupId: Long, userId: Long) {
        service.bot.requireCapability(BotCapability.GROUP_MEMBER_QUERY)
        val groupName = service.bot.getGroupName(groupId)
        val userName = service.bot.getGroupUserName(groupId, userId)
        val userCard = service.bot.getGroupUserCard(groupId, userId)
        service.context.messenger.sendMessage(
            getComponentFromMiniMsg(
                service.render(
                    Messages.ob2server,
                    "%group_name%" to groupName,
                    "%group_id%" to groupId.toString(),
                    "%user_nick%" to userCard,
                    "%message%" to message,
                    "%user_id%" to userId.toString(),
                    "%user_name%" to userName
                )
            )
        )
    }
}
