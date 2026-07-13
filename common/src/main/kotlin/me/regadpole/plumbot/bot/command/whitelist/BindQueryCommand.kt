package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import java.sql.SQLException

class BindQueryCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            requireDatabase()
            if (service.isAdmin(userId) && message.contains(" ")) {
                val msg = message.substring(1)
                when {
                    msg.startsWith("id:") -> {
                        val arg = msg.removePrefix("id:")
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

                    msg.startsWith("qq:") -> {
                        val argStr = msg.removePrefix("qq:")
                        val arg = argStr.toLongOrNull()
                        if (arg == null) {
                            service.sendWrongUsage(groupId)
                            return
                        }
                        val wl = DatabaseProvider.getBindByUser(arg.toString())
                        if (wl.isEmpty()) {
                            service.sendBindTemplate(
                                groupId,
                                Messages.qqEmptyBind,
                                "%user_id%" to arg.toString(),
                                "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                                "%user_nick%" to service.bot.getGroupUserCard(groupId, arg)
                            )
                            return
                        }
                        service.sendBindTemplate(
                            groupId,
                            Messages.adminQueryQQBind,
                            *service.originReplacements(groupId, userId),
                            "%user_id%" to arg.toString(),
                            "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, arg),
                            "%num%" to wl.size.toString(),
                            "%current%" to wl.keys.toString()
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
            service.sendBindTemplate(
                groupId,
                Messages.playerQueryBind,
                *service.userReplacements(groupId, userId),
                "%num%" to wl.size.toString(),
                "%current%" to wl.toString()
            )
            return
        } catch (e: IllegalStateException) {
            sendInternalError(groupId, e)
        } catch (e: SQLException) {
            sendInternalError(groupId, e)
        } catch (e: Exception) {
            sendInternalError(groupId, e)
        }
    }
}
