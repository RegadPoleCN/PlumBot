/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
