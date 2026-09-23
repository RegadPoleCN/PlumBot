package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import java.sql.SQLException

class BindRemoveCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            requireDatabase()
            val trimmedMessage = message.trim()
            if (trimmedMessage.isEmpty()) {
                service.sendWrongUsage(groupId)
                return
            }

            if (service.isAdmin(userId)) {
                if (trimmedMessage.startsWith("id:")) {
                    val playerName = trimmedMessage.removePrefix("id:").trim()
                    if (!service.checkPlayerExists(playerName)) {
                        service.sendBindTemplate(groupId, Messages.notExistsBind, "%player_name%" to playerName)
                        return
                    }
                    val targetUser = DatabaseProvider.getBindByName(playerName)
                    val targetUserId = targetUser?.toLongOrNull()
                    if (targetUserId == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    requireDatabase().removeBind(playerName)
                    service.context.playerService.kickPlayer(playerName)
                    val remaining = DatabaseProvider.getBindByUser(targetUser)
                    val remainingNames = remaining.keys.joinToString(", ")
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminDeleteBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to targetUser,
                        "%user_name%" to service.bot.getGroupUserName(groupId, targetUserId),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUserId),
                        "%target_player%" to playerName,
                        "%num%" to remaining.size.toString(),
                        "%current%" to remainingNames
                    )
                    return
                }

                if (trimmedMessage.startsWith("qq:")) {
                    val parts = trimmedMessage.removePrefix("qq:").trim().split(Regex("""\s+"""))
                    if (parts.size != 2) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    val targetQqStr = parts[0]
                    val playerName = parts[1]
                    val targetQq = targetQqStr.toLongOrNull()
                    if (targetQq == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    val userBinds = DatabaseProvider.getBindByUser(targetQqStr)
                    if (userBinds.isEmpty()) {
                        service.sendBindTemplate(
                            groupId,
                            Messages.qqEmptyBind,
                            "%user_id%" to targetQqStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, targetQq),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, targetQq)
                        )
                        return
                    }
                    val matchedPlayer = userBinds.keys.firstOrNull { it.equals(playerName, ignoreCase = true) }
                    if (matchedPlayer == null) {
                        service.sendBindTemplate(groupId, Messages.notBelongToYou, "%player_name%" to playerName)
                        return
                    }
                    requireDatabase().removeBind(matchedPlayer)
                    service.context.playerService.kickPlayer(matchedPlayer)
                    val remaining = DatabaseProvider.getBindByUser(targetQqStr)
                    val remainingNames = remaining.keys.joinToString(", ")
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminDeleteBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to targetQqStr,
                        "%user_name%" to service.bot.getGroupUserName(groupId, targetQq),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, targetQq),
                        "%target_player%" to matchedPlayer,
                        "%num%" to remaining.size.toString(),
                        "%current%" to remainingNames
                    )
                    return
                }
            }

            // 普通用户或管理员直接删除玩家名
            val userBinds = DatabaseProvider.getBindByUser(userId.toString())
            val matchedPlayer = userBinds.keys.firstOrNull { it.equals(trimmedMessage, ignoreCase = true) }
            if (matchedPlayer == null) {
                service.sendBindTemplate(groupId, Messages.notBelongToYou, "%player_name%" to trimmedMessage)
                return
            }
            requireDatabase().removeBind(matchedPlayer)
            service.context.playerService.kickPlayer(matchedPlayer)
            val remaining = DatabaseProvider.getBindByUser(userId.toString())
            val remainingNames = remaining.keys.joinToString(", ")
            service.sendBindTemplate(
                groupId,
                Messages.playerDeleteBind,
                *service.userReplacements(groupId, userId),
                "%target_player%" to matchedPlayer,
                "%num%" to remaining.size.toString(),
                "%current%" to remainingNames
            )
        } catch (e: IllegalStateException) {
            sendInternalError(groupId, e)
        } catch (e: SQLException) {
            sendInternalError(groupId, e)
        } catch (e: Exception) {
            sendInternalError(groupId, e)
        }
    }
}
