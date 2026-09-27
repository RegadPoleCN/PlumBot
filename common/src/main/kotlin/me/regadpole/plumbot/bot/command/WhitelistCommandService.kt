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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.bot.command.whitelist.BindApplyCommand
import me.regadpole.plumbot.bot.command.whitelist.BindQueryCommand
import me.regadpole.plumbot.bot.command.whitelist.BindRemoveCommand
import me.regadpole.plumbot.bot.command.whitelist.BindUserDecreaseCommand

class WhitelistCommandService(service: BotCommandService) {
    private val applyCommand = BindApplyCommand(service)
    private val queryCommand = BindQueryCommand(service)
    private val removeCommand = BindRemoveCommand(service)
    private val userDecreaseCommand = BindUserDecreaseCommand(service)

    fun apply(message: String, groupId: Long, userId: Long) = applyCommand.execute(message, groupId, userId)
    fun query(message: String, groupId: Long, userId: Long) = queryCommand.execute(message, groupId, userId)
    fun remove(message: String, groupId: Long, userId: Long) = removeCommand.execute(message, groupId, userId)
    fun onUserDecrease(groupId: Long, userId: Long) = userDecreaseCommand.execute("", groupId, userId)
}
