package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.config.Messages

class PlayerListCommandService(private val service: BotCommandService) {
    fun list(message: String, groupId: Long, userId: Long) {
        service.sendListTemplate(
            groupId,
            Messages.playerList,
            "%player_list%" to service.context.playerService.listPlayerString(),
            "%player_num%" to service.context.playerService.listPlayers().size.toString()
        )
        return
    }
}
