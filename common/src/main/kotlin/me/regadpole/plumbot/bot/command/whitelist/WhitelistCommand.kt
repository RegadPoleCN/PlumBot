package me.regadpole.plumbot.bot.command.whitelist

interface WhitelistCommand {
    fun execute(message: String, groupId: Long, userId: Long)
}
