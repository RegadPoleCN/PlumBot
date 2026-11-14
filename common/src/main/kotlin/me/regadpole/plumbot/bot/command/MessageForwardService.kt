// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.utils.escapeMiniMessageTags
import me.regadpole.plumbot.utils.getComponentFromMiniMsg

class MessageForwardService(private val service: BotCommandService) {
    fun forward(message: String, groupId: Long, userId: Long) {
        if (!service.isFeatureEnabled("message")) return
        val toServer = service.config.getBoolean("feature", "message", "to_server")
        if (!toServer) return

        val mode = service.config.getInteger("feature", "message", "mode")
        val prefix = service.config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(message, mode, prefix) ?: return

        val isBypass = service.isAdmin(userId)
        val filterResult = me.regadpole.plumbot.filter.FilterManagerHolder.manager?.process(resolved, isBypass = isBypass)
        if (filterResult?.isBlocked == true) {
            return
        }
        val finalMessage = filterResult?.sanitizedText ?: resolved

        sendOb2ServerMessage(finalMessage, groupId, userId)
    }

    /** 适配器缺 GROUP_MEMBER_QUERY 的降级只告警一次，避免每条转发刷屏。 */
    private var warnedNoMemberQuery = false

    private fun sendOb2ServerMessage(message: String, groupId: Long, userId: Long) {
        // 适配器不支持查询群成员资料时降级渲染：保住"群→服转发"主功能（此前能力缺失时
        // 异常被事件分发兜底吞掉，to_server 开了也一条都转不过去，且无任何日志提示）。
        val (groupName, userName, userCard) = if (BotCapability.GROUP_MEMBER_QUERY in service.bot.metadata.capabilities) {
            Triple(
                service.bot.getGroupName(groupId),
                service.bot.getGroupUserName(groupId, userId),
                service.bot.getGroupUserCard(groupId, userId)
            )
        } else {
            if (!warnedNoMemberQuery) {
                warnedNoMemberQuery = true
                service.context.log(
                    LogLevel.WARN,
                    "[PlumBot-Forward] 适配器 " + service.bot.metadata.type +
                        " 未声明 GROUP_MEMBER_QUERY 能力，转发消息中的群名/昵称将使用占位文本。"
                )
            }
            Triple("未知群组", "未知用户", "未知用户")
        }

        val rendered = service.render(
            Messages.ob2server,
            "%group_name%" to escapeMiniMessageTags(groupName),
            "%group_id%" to groupId.toString(),
            "%user_nick%" to escapeMiniMessageTags(userCard),
            "%message%" to escapeMiniMessageTags(message),
            "%user_id%" to userId.toString(),
            "%user_name%" to escapeMiniMessageTags(userName)
        )

        service.context.sendMessage(getComponentFromMiniMsg(rendered))
    }
}
