// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
                        // 单条"该角色名未绑定"是事实而不是列表：不强行套 bindList 卡片，
                        // 与 existsBind / notBelongToYou 等提示共用 sendBindTemplate 通道。
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
                        // "该用户没有绑定"是空列表，同样走 bindList 卡片：文字兜底仍是 qqEmptyBind，
                        // 图片开关与 elsewhere 的列表结果共用 render.outputs.bind。
                        service.sendBindList(
                            groupId = groupId,
                            title = "白名单查询 · ${service.bot.getGroupUserName(groupId, arg)}",
                            players = emptyList(),
                            userId = argStr,
                            limit = service.bindingLimit(),
                            fallbackText = service.render(
                                Messages.qqEmptyBind,
                                "%user_id%" to argStr,
                                "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                                "%user_nick%" to service.bot.getGroupUserCard(groupId, arg)
                            )
                        )
                        return
                    }
                    val formattedList = formatPlayerList(wl.keys)
                    // 列表型结果走 bindList 路由（whitelist 路由只承接单人成功绑定）
                    service.sendBindList(
                        groupId,
                        title = "白名单查询 · ${service.bot.getGroupUserName(groupId, arg)}",
                        players = wl.keys.toList(),
                        userId = argStr,
                        limit = service.bindingLimit(),
                        fallbackText = service.render(
                            Messages.adminQueryQQBind,
                            *service.originReplacements(groupId, userId),
                            "%user_id%" to argStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, arg),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, arg),
                            "%num%" to wl.size.toString(),
                            "%current%" to formattedList
                        )
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
            // 空列表也按列表卡片发：文字兜底保持 qqEmptyBind 原样。
            service.sendBindList(
                groupId = groupId,
                title = "我的白名单",
                players = emptyList(),
                userId = userId.toString(),
                limit = service.bindingLimit(),
                fallbackText = service.render(Messages.qqEmptyBind, *service.userReplacements(groupId, userId))
            )
            return
        }
        val formattedList = formatPlayerList(wl.keys)
        service.sendBindList(
            groupId,
            title = "我的白名单",
            players = wl.keys.toList(),
            userId = userId.toString(),
            limit = service.bindingLimit(),
            fallbackText = service.render(
                Messages.playerQueryBind,
                *service.userReplacements(groupId, userId),
                "%num%" to wl.size.toString(),
                "%current%" to formattedList
            )
        )
    }

    private fun formatPlayerList(players: Collection<String>): String {
        if (players.isEmpty()) return "无"
        return players.joinToString(separator = "\n  • ", prefix = "\n  • ")
    }
}
