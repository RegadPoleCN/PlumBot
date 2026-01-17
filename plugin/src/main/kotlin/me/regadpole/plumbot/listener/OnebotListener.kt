package me.regadpole.plumbot.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.Onebot
import me.regadpole.plumbot.utils.runTask
import top.alazeprt.aonebot.event.Listener
import top.alazeprt.aonebot.event.SubscribeBotEvent
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.message.PrivateMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class OnebotListener(private val onebot: Onebot): Listener {
    @SubscribeBotEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        if (!PlumBot.INSTANCE.config!!.getLongListFromConfig("groups").contains(event.groupId)) return
        runTask{
            onebot.handler?.onGroupMessage(event)
        }
    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        runTask {
            onebot.handler?.onUserDecrease(event)
        }
    }
}