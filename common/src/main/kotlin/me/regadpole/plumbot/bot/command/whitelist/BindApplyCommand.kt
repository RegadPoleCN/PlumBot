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

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.internal.PlayerNameRules
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.database.DatabaseProvider

class BindApplyCommand(service: BotCommandService) : AbstractWhitelistCommand(service) {

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
                    // 此处**不做** require_in_group 校验：管理员自己发的群消息，
                    // 已由消息本身证明其在群内，查询不带来任何新信息。
                    // 群内路径必须记录来源群：退本群时才解绑这一条，不牵连他在别的群的绑定
                    when (
                        service.bindPolicy.bind(
                            requireDatabase(), userId, playerName,
                            bypassLimit = adminBypass, sourceGroup = groupId.toString(),
                        )
                    ) {
                        BindPolicy.Result.LimitReached -> {
                            sendFullBindNotice(groupId, userId)
                            return
                        }

                        BindPolicy.Result.PlayerAlreadyBound -> {
                            service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                            return
                        }

                        BindPolicy.Result.Added -> Unit
                    }
                    val wl = DatabaseProvider.getBindByUser(userId.toString())
                    // 管理员给自己申请与成员自助申请产出同一种业务结果，走同一张 whitelist 卡片，
                    // 不再落到 sendBindTemplate（那条路径由 render.outputs.text 门控，默认关闭）。
                    service.sendWhitelistCard(
                        groupId = groupId,
                        playerName = playerName,
                        qq = userId.toString(),
                        bindTime = DatabaseProvider.getBindingByName(playerName)?.bindingTime?.toString(),
                        fallbackText = service.render(
                            Messages.playerAddBind,
                            *service.userReplacements(groupId, userId),
                            "%target_player%" to playerName,
                            "%num%" to wl.size.toString(),
                            "%current%" to wl.keys.joinToString(", ")
                        ),
                        bindCount = wl.size,
                        current = wl.keys.toList()
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
                    // 管理员代绑：**唯一**需要校验的路径。
                    // 前两条自助路径的"在群"已由群消息本身证明，查询无新信息却可能误拒；
                    // 被代绑的 QQ 没有发过任何消息，这才是真正未知的量。
                    if (!service.ensureTargetInGroup(groupId, targetUserId)) return
                    when (
                        service.bindPolicy.bind(
                            requireDatabase(), targetUserId, playerName,
                            bypassLimit = adminBypass, sourceGroup = groupId.toString(),
                        )
                    ) {
                        BindPolicy.Result.LimitReached -> {
                            sendFullBindNotice(groupId, targetUserId)
                            return
                        }

                        BindPolicy.Result.PlayerAlreadyBound -> {
                            service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                            return
                        }

                        BindPolicy.Result.Added -> Unit
                    }
                    val wl = DatabaseProvider.getBindByUser(targetUserIdStr)
                    // 单人成功绑定走定制卡片（whitelist 路由）
                    service.sendWhitelistCard(
                        groupId = groupId,
                        playerName = playerName,
                        qq = targetUserIdStr,
                        bindTime = DatabaseProvider.getBindingByName(playerName)?.bindingTime?.toString(),
                        fallbackText = service.render(
                            Messages.adminAddBind,
                            *service.originReplacements(groupId, userId),
                            "%user_id%" to targetUserIdStr,
                            "%user_name%" to service.bot.getGroupUserName(groupId, targetUserId),
                            "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUserId),
                            "%target_player%" to playerName,
                            "%num%" to wl.size.toString(),
                            "%current%" to wl.keys.joinToString(", ")
                        ),
                        originId = userId.toString(),
                        originName = service.bot.getGroupUserCard(groupId, userId),
                        bindCount = wl.size,
                        current = wl.keys.toList()
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
            // 此处**不做** require_in_group 校验：申请人是在本群发出的消息，
            // 成员身份已确立；查询只会引入"超时被误判为不在群"的风险。
            when (
                service.bindPolicy.bind(
                    requireDatabase(), userId, playerName,
                    bypassLimit = false, sourceGroup = groupId.toString(),
                )
            ) {
                BindPolicy.Result.LimitReached -> {
                    sendFullBindNotice(groupId, userId)
                    return
                }

                BindPolicy.Result.PlayerAlreadyBound -> {
                    service.sendBindTemplate(groupId, Messages.existsBind, "%player_name%" to playerName)
                    return
                }

                BindPolicy.Result.Added -> Unit
            }
            val wl = DatabaseProvider.getBindByUser(userId.toString())
            // 单人成功绑定走定制卡片（whitelist 路由）
            service.sendWhitelistCard(
                groupId = groupId,
                playerName = playerName,
                qq = userId.toString(),
                bindTime = DatabaseProvider.getBindingByName(playerName)?.bindingTime?.toString(),
                fallbackText = service.render(
                    Messages.playerAddBind,
                    *service.userReplacements(groupId, userId),
                    "%target_player%" to playerName,
                    "%num%" to wl.size.toString(),
                    "%current%" to wl.keys.joinToString(", ")
                ),
                bindCount = wl.size,
                current = wl.keys.toList()
            )
            return
        }
    }

    private fun isValidPlayerName(name: String): Boolean = PlayerNameRules.isValid(name)

    private fun sendFullBindNotice(groupId: Long, targetUser: Long) {
        val wl = DatabaseProvider.getBindByUser(targetUser.toString())
        // 列表型提示走 bindList 路由；注意 %current% 在这里语义是**数量**，%player_name% 才是名单
        service.sendBindList(
            groupId = groupId,
            title = "白名单已满 · ${service.bot.getGroupUserName(groupId, targetUser)}",
            players = wl.keys.toList(),
            userId = targetUser.toString(),
            limit = service.bindingLimit(),
            fallbackText = service.render(
                Messages.fullBind,
                "%player_name%" to wl.keys.joinToString(", "),
                "%current%" to wl.size.toString(),
                "%user_id%" to targetUser.toString(),
                "%whitelist_limit%" to service.config.getInteger("feature", "bind", "maxNum").toString(),
                "%user_name%" to service.bot.getGroupUserName(groupId, targetUser),
                "%user_nick%" to service.bot.getGroupUserCard(groupId, targetUser)
            )
        )
    }
}
