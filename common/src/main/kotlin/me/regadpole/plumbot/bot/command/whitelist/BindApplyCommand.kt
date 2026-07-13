package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import java.sql.SQLException

class BindApplyCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            requireDatabase()
            val args = message.split(" ")
            if (service.isAdmin(userId)) {
                when (args.size) {
                    1 -> {
                        val playerName = message
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
                            "%current%" to wl.keys.toString()
                        )
                        return
                    }

                    2 -> {
                        val targetUserIdStr = args[0]
                        val playerName = args[1]
                        if (service.checkPlayerExists(playerName)) {
                            service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                            return
                        }
                        val targetUserId = targetUserIdStr.toLongOrNull()
                        if (targetUserId == null) {
                            service.sendWrongUsage(groupId)
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
                            "%current%" to wl.keys.toString()
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
                if (service.checkUserBindingFull(userId.toString())) {
                    val wl = DatabaseProvider.getBindByUser(userId.toString())
                    service.sendBindTemplate(
                        groupId,
                        Messages.fullBind,
                        "%player_name%" to wl.keys.toString(),
                        "%current%" to wl.size.toString(),
                        "%user_id%" to userId.toString(),
                        "%whitelist_limit%" to service.config.getInteger("feature", "bind", "maxNum").toString(),
                        "%user_name%" to service.bot.getGroupUserName(groupId, userId),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, userId)
                    )
                    return
                }
                if (service.checkPlayerExists(message)) {
                    service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to message)
                    return
                }
                requireDatabase().addBind(userId, message)
                val wl = DatabaseProvider.getBindByUser(userId.toString())
                service.sendBindTemplate(
                    groupId,
                    Messages.playerAddBind,
                    *service.userReplacements(groupId, userId),
                    "%target_player%" to message,
                    "%num%" to wl.size.toString(),
                    "%current%" to wl.keys.toString()
                )
                return
            }
        } catch (e: IllegalStateException) {
            sendInternalError(groupId, e)
        } catch (e: SQLException) {
            sendInternalError(groupId, e)
        } catch (e: Exception) {
            sendInternalError(groupId, e)
        }
    }
}
