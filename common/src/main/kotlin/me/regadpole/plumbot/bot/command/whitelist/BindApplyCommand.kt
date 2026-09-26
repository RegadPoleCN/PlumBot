package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider

class BindApplyCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    private val playerNameRegex = Regex("""^[a-zA-Z0-9_\.*]{3,16}$""")

    override fun handle(message: String, groupId: Long, userId: Long) {
        val args = message.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val isAdmin = service.isAdmin(userId)
        val adminBypass = service.config.getBoolean("feature", "bind", "adminBypass")

        if (isAdmin) {
            when (args.size) {
                1 -> {
                    val playerName = args[0]
                    if (!isValidPlayerName(playerName)) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    if (!adminBypass && service.checkUserBindingFull(userId.toString())) {
                        sendFullBindNotice(groupId, userId)
                        return
                    }
                    if (service.checkPlayerExists(playerName)) {
                        service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                        return
                    }
                    requireDatabase().addBind(userId, playerName)
                    val wl = DatabaseProvider.getBindByUser(userId.toString())
                    service.sendBindTemplate(
                        groupId,
                        Messages.playerAddBind,
                        *service.userReplacements(groupId, userId),
                        "%target_player%" to playerName,
                        "%num%" to wl.size.toString(),
                        "%current%" to wl.keys.joinToString(", ")
                    )
                    return
                }

                2 -> {
                    val targetUserIdStr = args[0]
                    val playerName = args[1]
                    if (!isValidPlayerName(playerName)) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    val targetUserId = targetUserIdStr.toLongOrNull()
                    if (targetUserId == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    if (!adminBypass && service.checkUserBindingFull(targetUserIdStr)) {
                        sendFullBindNotice(groupId, targetUserId)
                        return
                    }
                    if (service.checkPlayerExists(playerName)) {
                        service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                        return
                    }
                    requireDatabase().addBind(targetUserId, playerName)
                    val wl = DatabaseProvider.getBindByUser(targetUserIdStr)
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminAddBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to targetUserIdStr,
                        "%user_name%" to service.bot.getGroupUserName(groupId, targetUserId),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUserId),
                        "%target_player%" to playerName,
                        "%num%" to wl.size.toString(),
                        "%current%" to wl.keys.joinToString(", ")
                    )
                    return
                }

                else -> service.sendWrongUsage(groupId)
            }
        } else {
            if (args.size != 1) {
                service.sendWrongUsage(groupId)
                return
            }
            val playerName = args[0]
            if (!isValidPlayerName(playerName)) {
                service.sendWrongUsage(groupId)
                return
            }
            if (service.checkUserBindingFull(userId.toString())) {
                sendFullBindNotice(groupId, userId)
                return
            }
            if (service.checkPlayerExists(playerName)) {
                service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                return
            }
            requireDatabase().addBind(userId, playerName)
            val wl = DatabaseProvider.getBindByUser(userId.toString())
            service.sendBindTemplate(
                groupId,
                Messages.playerAddBind,
                *service.userReplacements(groupId, userId),
                "%target_player%" to playerName,
                "%num%" to wl.size.toString(),
                "%current%" to wl.keys.joinToString(", ")
            )
            return
        }
    }

    private fun isValidPlayerName(name: String): Boolean = playerNameRegex.matches(name)

    private fun sendFullBindNotice(groupId: Long, targetUser: Long) {
        val wl = DatabaseProvider.getBindByUser(targetUser.toString())
        service.sendBindTemplate(
            groupId,
            Messages.fullBind,
            "%player_name%" to wl.keys.joinToString(", "),
            "%current%" to wl.size.toString(),
            "%user_id%" to targetUser.toString(),
            "%whitelist_limit%" to service.config.getInteger("feature", "bind", "maxNum").toString(),
            "%user_name%" to service.bot.getGroupUserName(groupId, targetUser),
            "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUser)
        )
    }
}
