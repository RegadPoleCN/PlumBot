package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider

class BindUserDecreaseCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun handle(message: String, groupId: Long, userId: Long) {
        DatabaseProvider.getBindByUser(userId.toString()).keys.forEach {
            service.context.playerService.kickPlayer(it)
        }
        requireDatabase().removeBind(userId)
    }
}
