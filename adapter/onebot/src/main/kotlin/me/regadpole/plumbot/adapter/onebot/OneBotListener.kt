// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
        // 成员缓存的失效已收敛到 BotEventDispatcher.dispatchUserDecrease（三端统一），
        // 此处不再自行失效，避免同一件事出现两个实现而再次跑偏。
        TaskProviderImpl.submitAsync {
            BotEventDispatcher.dispatchUserDecrease(event.groupId, event.userId)
        }
    }
}
