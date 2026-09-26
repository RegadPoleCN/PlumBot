package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.LogLevel
import java.sql.SQLException

abstract class AbstractWhitelistCommand(protected val service: BotCommandService) : WhitelistCommand {

    protected abstract fun handle(message: String, groupId: Long, userId: Long)

    final override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            requireDatabase()
            handle(message, groupId, userId)
        } catch (e: Throwable) {
            sendInternalError(groupId, e)
        }
    }

    protected fun requireDatabase(): IDatabase {
        return DatabaseProvider.getDatabase()
            ?: throw IllegalStateException("Database is not initialized")
    }

    protected fun sendInternalError(groupId: Long, e: Throwable) {
        service.context.log(LogLevel.ERROR, e.stackTraceToString())
        service.sendBindMessage(groupId, Messages.internalError)
    }
}
