package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.utils.escapeMiniMessageTags
import me.regadpole.plumbot.utils.getComponentFromMiniMsg

class MessageForwardService(private val service: BotCommandService) {
    fun forward(message: String, groupId: Long, userId: Long) {
        if (!service.isFeatureEnabled("message")) return
        val toServer = service.config.getBoolean("feature", "message", "to_server")
        if (!toServer) return

        val mode = service.config.getInteger("feature", "message", "mode")
        val prefix = service.config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(message, mode, prefix) ?: return

        val isBypass = service.isAdmin(userId)
        val filterResult = me.regadpole.plumbot.filter.FilterManagerHolder.manager?.process(resolved, isBypass = isBypass)
        if (filterResult?.isBlocked == true) {
            return
        }
        val finalMessage = filterResult?.sanitizedText ?: resolved

        sendOb2ServerMessage(finalMessage, groupId, userId)
    }

    private fun sendOb2ServerMessage(message: String, groupId: Long, userId: Long) {
        service.bot.requireCapability(BotCapability.GROUP_MEMBER_QUERY)
        val groupName = service.bot.getGroupName(groupId)
        val userName = service.bot.getGroupUserName(groupId, userId)
        val userCard = service.bot.getGroupUserCard(groupId, userId)

        val rendered = service.render(
            Messages.ob2server,
            "%group_name%" to escapeMiniMessageTags(groupName),
            "%group_id%" to groupId.toString(),
            "%user_nick%" to escapeMiniMessageTags(userCard),
            "%message%" to escapeMiniMessageTags(message),
            "%user_id%" to userId.toString(),
            "%user_name%" to escapeMiniMessageTags(userName)
        )

        service.context.messenger.sendMessage(getComponentFromMiniMsg(rendered))
    }
}
