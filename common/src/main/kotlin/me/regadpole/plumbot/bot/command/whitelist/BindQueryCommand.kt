package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider

class BindQueryCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun handle(message: String, groupId: Long, userId: Long) {
        val trimmedMessage = message.trim()
        if (service.isAdmin(userId) && trimmedMessage.isNotEmpty()) {
            when {
                trimmedMessage.startsWith("id:") -> {
                    val arg = trimmedMessage.removePrefix("id:").trim()
                    val wl = DatabaseProvider.getBindByName(arg)
                    if (wl == null) {
                        service.sendBindTemplate(groupId, Messages.idEmptyBind, "%player%" to arg)
                        return
                    }
                    val targetUserId = wl.toLongOrNull()
                    if (targetUserId == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminQueryIdBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to wl,
                        "%user_name%" to service.bot.getGroupUserName(groupId, targetUserId),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUserId),
                        "%player%" to arg
                    )
                    return
                }

                trimmedMessage.startsWith("qq:") -> {
                    val argStr = trimmedMessage.removePrefix("qq:").trim()
                    val arg = argStr.toLongOrNull()
                    if (arg == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    val wl = DatabaseProvider.getBindByUser(argStr)
                    if (wl.isEmpty()) {
                        service.sendBindTemplate(
                            groupId,
                            Messages.qqEmptyBind,
                            "%user_id%" to argStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, arg)
                        )
                        return
                    }
                    val formattedList = formatPlayerList(wl.keys)
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminQueryQQBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to argStr,
                        "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, arg),
                        "%num%" to wl.size.toString(),
                        "%current%" to formattedList
                    )
                    return
                }

                else -> {
                    service.sendWrongUsage(groupId)
                    return
                }
            }
        }
        val wl = DatabaseProvider.getBindByUser(userId.toString())
        if (wl.isEmpty()) {
            service.sendBindTemplate(groupId, Messages.qqEmptyBind, *service.userReplacements(groupId, userId))
            return
        }
        val formattedList = formatPlayerList(wl.keys)
        service.sendBindTemplate(
            groupId,
            Messages.playerQueryBind,
            *service.userReplacements(groupId, userId),
            "%num%" to wl.size.toString(),
            "%current%" to formattedList
        )
    }

    private fun formatPlayerList(players: Collection<String>): String {
        if (players.isEmpty()) return "无"
        return players.joinToString(separator = "\n  • ", prefix = "\n  • ")
    }
}
