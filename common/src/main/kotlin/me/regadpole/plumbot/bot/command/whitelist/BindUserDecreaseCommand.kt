package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import java.sql.SQLException

class BindUserDecreaseCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            DatabaseProvider.getBindByUser(userId.toString()).keys.forEach {
                service.context.playerService.kickPlayer(it)
            }
            requireDatabase().removeBind(userId)
        } catch (e: IllegalStateException) {
            sendInternalError(groupId, e)
        } catch (e: SQLException) {
            sendInternalError(groupId, e)
        } catch (e: Exception) {
            sendInternalError(groupId, e)
        }
    }
}
