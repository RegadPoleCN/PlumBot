package me.regadpole.plumbot.adapter.onebot

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
            var message = ""
            event.jsonMessage.forEach {
                val jsonObject = it.asJsonObject ?: return@forEach
                val type = jsonObject.get("type")?.asString ?: return@forEach
                val data = jsonObject.get("data")?.asJsonObject ?: return@forEach
                when (type) {
                    "text" -> message += data.get("text")?.asString ?: return@forEach
                    "image" -> message += "[图片]"
                    "at" -> {
                        val qq = data.get("qq")?.asLong ?: return@forEach
                        message += onebot.getGroupUserCard(event.groupId, qq)
                    }
                }
            }
            onebot.handler?.onGroupMessage(message, event.groupId, event.senderId)
        }
    }

    @SubscribeBotEvent
    fun onUserMessage(event: PrivateMessageEvent) {}

    @SubscribeBotEvent
    fun onGroupMemberDecrease(event: GroupMemberDecreaseEvent) {
        if (!onebot.context.config.getLongList("groups").contains(event.groupId)) return
        onebot.groupMemberCache.invalidate(event.groupId, event.userId)
        TaskProviderImpl.submitAsync { onebot.handler?.onUserDecrease(event.groupId, event.userId) }
    }
}
