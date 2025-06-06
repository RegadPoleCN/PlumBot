package me.regadpole.plumbot.listener

import me.regadpole.plumbot.bot.Onebot
import top.alazeprt.aonebot.event.Listener
import top.alazeprt.aonebot.event.SubscribeBotEvent
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.message.PrivateMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class OnebotListener(private val onebot: Onebot): Listener {
    @SubscribeBotEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        if (!onebot.plugin.config.getLongListFromConfig("groups").contains(event.groupId)) return
        onebot.handler?.onGroupMessage(event)
    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        onebot.handler?.onUserDecrease(event)
    }
}