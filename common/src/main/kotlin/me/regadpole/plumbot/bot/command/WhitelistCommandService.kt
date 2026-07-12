package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.internal.LogLevel
import java.sql.SQLException

class WhitelistCommandService(private val service: BotCommandService) {

    fun apply(message: String, groupId: Long, userId: Long) {
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
        } catch (e: RuntimeException) {
            sendInternalError(groupId, e)
        }
    }

    fun query(message: String, groupId: Long, userId: Long) {
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
        } catch (e: RuntimeException) {
            sendInternalError(groupId, e)
        }
    }

    fun remove(message: String, groupId: Long, userId: Long) {
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
        } catch (e: RuntimeException) {
            sendInternalError(groupId, e)
        }
    }

    fun onUserDecrease(groupId: Long, userId: Long) {
        try {
            DatabaseProvider.getBindByUser(userId.toString()).keys.forEach {
                service.context.playerService.kickPlayer(it)
            }
            requireDatabase().removeBind(userId)
        } catch (e: IllegalStateException) {
            sendInternalError(groupId, e)
        } catch (e: SQLException) {
            sendInternalError(groupId, e)
        } catch (e: RuntimeException) {
            sendInternalError(groupId, e)
        }
    }

    private fun requireDatabase(): IDatabase {
        return DatabaseProvider.getDatabase()
            ?: throw IllegalStateException("Database is not initialized")
    }

    private fun sendInternalError(groupId: Long, e: Throwable) {
        service.context.logger.log(LogLevel.ERROR, e.stackTraceToString())
        service.sendBindMessage(groupId, Messages.internalError)
    }
}
