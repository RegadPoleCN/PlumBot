package me.regadpole.plumbot.listener.qq

import cn.evole.onebot.client.annotations.SubscribeEvent
import cn.evole.onebot.client.interfaces.Listener
import cn.evole.onebot.sdk.event.message.GroupMessageEvent
import cn.evole.onebot.sdk.event.message.PrivateMessageEvent
import cn.evole.onebot.sdk.event.notice.group.GroupDecreaseNoticeEvent
import me.regadpole.plumbot.listener.GPlatformListener


class QQListener: Listener {
    @SubscribeEvent
    fun onGroupMessage(event: GroupMessageEvent) {
        GPlatformListener.onGroupMessage(event.message, event.groupId.toString(), event.sender.userId, event.sender.nickname)
    }

    @SubscribeEvent
    fun onPrivateMessage(e: PrivateMessageEvent) {

    }

    /**
     * unstable
     */
    @SubscribeEvent
    fun onGroupMemberDecreased(e: GroupDecreaseNoticeEvent) {}
}