package me.regadpole.plumbot.listener.qq

import cn.evole.onebot.client.annotations.SubscribeEvent
import cn.evole.onebot.client.interfaces.Listener
import cn.evole.onebot.sdk.event.message.GroupMessageEvent
import cn.evole.onebot.sdk.event.message.PrivateMessageEvent
import cn.evole.onebot.sdk.event.notice.group.GroupDecreaseNoticeEvent

class QQListener: Listener {
    @SubscribeEvent
    fun onGroupMessage(event: GroupMessageEvent) {
    }

    @SubscribeEvent
    fun onPrivateMessage(e: PrivateMessageEvent) {
    }

    /**
     * unstable
     */
    @SubscribeEvent
    fun onGroupMemberDecreased(e: GroupDecreaseNoticeEvent) {
    }
}