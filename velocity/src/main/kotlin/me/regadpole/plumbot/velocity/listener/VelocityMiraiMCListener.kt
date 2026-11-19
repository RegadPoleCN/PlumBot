// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.velocity.listener

import com.velocitypowered.api.event.Subscribe
import me.dreamvoid.miraimc.velocity.event.group.member.MiraiMemberJoinEvent
import me.dreamvoid.miraimc.velocity.event.group.member.MiraiMemberLeaveEvent
import me.dreamvoid.miraimc.velocity.event.message.passive.MiraiGroupMessageEvent
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.adapter.miraimc.MiraiCodeParser
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bot.BotProvider

class VelocityMiraiMCListener(private val plugin: PlumBot) {

    @Subscribe
    fun onGroupMessage(event: MiraiGroupMessageEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val rawMessage = event.message
        val groupId = event.groupID
        val senderId = event.senderID
        plugin.platform.scheduler.runAsync {
            val bot = BotProvider.getBot()
            val parsedMessage = MiraiCodeParser.parse(rawMessage) { targetQq ->
                bot?.getGroupUserCard(groupId, targetQq) ?: targetQq.toString()
            }
            BotEventDispatcher.dispatchGroupMessage(parsedMessage, groupId, senderId)
        }
    }

    @Subscribe
    fun onGroupDecrease(event: MiraiMemberLeaveEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val groupId = event.groupID
        val memberId = event.targetID
        plugin.platform.scheduler.runAsync {
            BotEventDispatcher.dispatchUserDecrease(groupId, memberId)
        }
    }

    @Subscribe
    fun onGroupIncrease(event: MiraiMemberJoinEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val groupId = event.groupID
        val memberId = event.newMemberID
        // 进群失效"不在群内"的负缓存，与退群对称，统一走中心分发点。
        plugin.platform.scheduler.runAsync {
            BotEventDispatcher.dispatchUserIncrease(groupId, memberId)
        }
    }
}
