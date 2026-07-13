package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider
import java.sql.SQLException

class BindRemoveCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun execute(message: String, groupId: Long, userId: Long) {
        try {
            requireDatabase()
            if (service.isAdmin(userId)) {
                if (message.startsWith("id:")) {
                    val arg = message.removePrefix("id:")
                    if (!service.checkPlayerExists(arg)) {
                        service.sendBindTemplate(groupId, Messages.notExistsBind, "%player_name%" to arg)
                        return
                    }
                    val target = DatabaseProvider.getBindByName(arg)?.toLongOrNull()
                    if (target == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    requireDatabase().removeBind(arg)
                    service.context.playerService.kickPlayer(arg)
                    val wl = DatabaseProvider.getBindByUser(target.toString())
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminDeleteBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to target.toString(),
                        "%user_name%" to service.bot.getGroupUserName(groupId, target),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, target),
                        "%target_player%" to arg,
                        "%num%" to wl.size.toString(),
                        "%current%" to wl.keys.toString()
                    )
                    return
                }

                if (message.startsWith("qq:")) {
                    val args = message.removePrefix("qq:").split(" ")
                    if (args.size != 2) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    val qqStr = args[0]
                    val indexStr = args[1]
                    val qq = qqStr.toLongOrNull()
                    val index = indexStr.toIntOrNull()
                    if (qq == null || index == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    if (DatabaseProvider.getBindByUser(qqStr).isEmpty()) {
                        service.sendBindTemplate(
                            groupId,
                            Messages.qqEmptyBind,
                            "%user_id%" to qqStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, qq),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, qq)
                        )
                        return
                    }
                    val target = requireDatabase().removeBindByNum(qq, index)
                    if (target == null) {
                        service.sendWrongUsage(groupId)
                        return
                    }
                    target.let { service.context.playerService.kickPlayer(it) }
                    var wl = DatabaseProvider.getBindByUser(qqStr)
                    if (wl.isEmpty()) wl = LinkedHashMap()
                    service.sendBindTemplate(
                        groupId,
                        Messages.adminDeleteBind,
                        *service.originReplacements(groupId, userId),
                        "%user_id%" to qqStr,
                        "%user_name%" to service.bot.getGroupUserName(groupId, qq),
                        "%user_nick%" to service.bot.getGroupUserCard(groupId, qq),
                        "%target_player%" to target,
                        "%num%" to wl.size.toString(),
                        "%current%" to wl.keys.toString()
                    )
                    return
                }
            }

            val index = message.toIntOrNull()
            if (index != null) {
                val target = requireDatabase().removeBindByNum(userId, index)
                if (target == null) {
                    service.sendWrongUsage(groupId)
                    return
                }
                target.let { service.context.playerService.kickPlayer(it) }
                var wl = DatabaseProvider.getBindByUser(userId.toString())
                if (wl.isEmpty()) wl = LinkedHashMap()
                service.sendBindTemplate(
                    groupId,
                    Messages.playerDeleteBind,
                    *service.userReplacements(groupId, userId),
                    "%target_player%" to target,
                    "%num%" to wl.size.toString(),
                    "%current%" to wl.keys.toString()
                )
                return
            }

            if (!service.checkPlayerBelongToUser(message, userId.toString())) {
                service.sendBindTemplate(groupId, Messages.notBelongToYou, "%player_name%" to message)
                return
            }
            requireDatabase().removeBind(message)
            service.context.playerService.kickPlayer(message)
            var wl = DatabaseProvider.getBindByUser(userId.toString())
            if (wl.isEmpty()) wl = LinkedHashMap()
            service.sendBindTemplate(
                groupId,
                Messages.playerDeleteBind,
                *service.userReplacements(groupId, userId),
                "%target_player%" to message,
                "%num%" to wl.size.toString(),
                "%current%" to wl.keys.toString()
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
