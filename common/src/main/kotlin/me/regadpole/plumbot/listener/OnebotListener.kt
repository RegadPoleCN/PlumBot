package me.regadpole.plumbot.listener

import me.regadpole.plumbot.bot.Onebot
import me.regadpole.plumbot.task.TaskProviderImpl
import top.alazeprt.aonebot.event.Listener
import top.alazeprt.aonebot.event.SubscribeBotEvent
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.message.PrivateMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class OnebotListener(private val onebot: Onebot): Listener {
    @SubscribeBotEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        if (!onebot.plugin.config.getLongList("groups").contains(event.groupId)) return
        TaskProviderImpl.submitAsync {
            var message = ""
            event.jsonMessage.forEach {
                val jsonObject = it.asJsonObject ?: return@forEach
                when (jsonObject.get("type").asString) {
                    "text" -> message += jsonObject.get("data").asJsonObject.get("text").asString
                    "image" -> message += "[图片]"
                    "at" -> message += onebot.getGroupUserCard(event.groupId, jsonObject.get("data").asJsonObject.get("qq").asLong)
                }
            }
            onebot.handler?.onGroupMessage(message, event.groupId, event.senderId)
        }

    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        if (!onebot.plugin.config.getLongList("groups").contains(event.groupId)) return
        TaskProviderImpl.submitAsync { onebot.handler?.onUserDecrease(event.groupId, event.userId) }
    }
}