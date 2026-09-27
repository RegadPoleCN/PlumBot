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

package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.task.TaskProviderImpl
import top.alazeprt.aonebot.event.Listener
import top.alazeprt.aonebot.event.SubscribeBotEvent
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.message.PrivateMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class OneBotListener(private val onebot: OneBotAdapter): Listener {
    @SubscribeBotEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        if (!onebot.context.config.getLongList("groups").contains(event.groupId)) return
        TaskProviderImpl.submitAsync {
            val message = OneBotMessageParser.parse(event.jsonMessage, event.groupId) { qq ->
                onebot.getGroupUserCard(event.groupId, qq)
            }
            BotEventDispatcher.dispatchGroupMessage(message, event.groupId, event.senderId)
        }
    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        if (!onebot.context.config.getLongList("groups").contains(event.groupId)) return
        onebot.groupMemberCache.invalidate(event.groupId, event.userId)
        TaskProviderImpl.submitAsync {
            BotEventDispatcher.dispatchUserDecrease(event.groupId, event.userId)
        }
    }
}
