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
