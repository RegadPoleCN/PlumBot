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

package me.regadpole.plumbot.bukkit.listener

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
}
