// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.listener

import me.dreamvoid.miraimc.bukkit.event.group.member.MiraiMemberJoinEvent
import me.dreamvoid.miraimc.bukkit.event.group.member.MiraiMemberLeaveEvent
import me.dreamvoid.miraimc.bukkit.event.message.passive.MiraiGroupMessageEvent
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.adapter.miraimc.MiraiCodeParser
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.bot.BotEventDispatcher
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class MiraiMCListener(private val plugin: PlumBot): Listener {
    @EventHandler
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

    @EventHandler
    fun onGroupDecrease(event: MiraiMemberLeaveEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val groupId = event.groupID
        val targetId = event.targetID
        plugin.platform.scheduler.runAsync {
            BotEventDispatcher.dispatchUserDecrease(groupId, targetId)
        }
    }

    @EventHandler
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
