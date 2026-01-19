package me.regadpole.plumbot.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.Onebot
import me.regadpole.plumbot.utils.runTaskAsync
import top.alazeprt.aonebot.event.Listener
import top.alazeprt.aonebot.event.SubscribeBotEvent
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.message.PrivateMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class OnebotListener(private val onebot: Onebot): Listener {
    @SubscribeBotEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        if (!PlumBot.INSTANCE.config!!.getLongListFromConfig("groups").contains(event.groupId)) return
        runTaskAsync{
            onebot.handler?.onGroupMessage(event)
        }
    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        runTaskAsync {
            onebot.handler?.onUserDecrease(event)
        }
    }
}