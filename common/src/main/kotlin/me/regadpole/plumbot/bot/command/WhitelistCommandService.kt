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
