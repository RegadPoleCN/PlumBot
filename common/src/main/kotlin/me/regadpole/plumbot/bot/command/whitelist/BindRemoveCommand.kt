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

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider

class BindRemoveCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

    override fun handle(message: String, groupId: Long, userId: Long) {
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
                    val remaining = DatabaseProvider.getBindByUser(targetUserId)
                    val remainingNames = remaining.keys.joinToString(", ")
                    // 删除结果与申请共用 bindList 路由：走 render.outputs.bind 的同一道图片门控，
                    // 正文真源仍是 adminDeleteBind，图片版与文字版同源。
                    service.sendBindList(
                        groupId = groupId,
                        title = "白名单已删除 · $playerName",
                        players = remaining.keys.toList(),
                        userId = targetUserId.toString(),
                        limit = service.bindingLimit(),
                        fallbackText = service.render(
                            Messages.adminDeleteBind,
                            *service.originReplacements(groupId, userId),
                            "%user_id%" to targetUserId.toString(),
                            "%user_name%" to service.bot.getGroupUserName(groupId, targetUserId),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUserId),
                            "%target_player%" to playerName,
                            "%num%" to remaining.size.toString(),
                            "%current%" to remainingNames
                        )
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
                    // 同上：列表型删除结果统一走 bindList（bind 门控），文字兜底仍是 adminDeleteBind。
                    service.sendBindList(
                        groupId = groupId,
                        title = "白名单已删除 · $matchedPlayer",
                        players = remaining.keys.toList(),
                        userId = targetQqStr,
                        limit = service.bindingLimit(),
                        fallbackText = service.render(
                            Messages.adminDeleteBind,
                            *service.originReplacements(groupId, userId),
                            "%user_id%" to targetQqStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, targetQq),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, targetQq),
                            "%target_player%" to matchedPlayer,
                            "%num%" to remaining.size.toString(),
                            "%current%" to remainingNames
                        )
                    )
                    return
                }
            }

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
            // 自主删除与自助申请走同一套卡片门控；文字兜底仍是 playerDeleteBind。
            service.sendBindList(
                groupId = groupId,
                title = "白名单已删除 · $matchedPlayer",
                players = remaining.keys.toList(),
                userId = userId.toString(),
                limit = service.bindingLimit(),
                fallbackText = service.render(
                    Messages.playerDeleteBind,
                    *service.userReplacements(groupId, userId),
                    "%target_player%" to matchedPlayer,
                    "%num%" to remaining.size.toString(),
                    "%current%" to remainingNames
                )
            )
        }
    }
