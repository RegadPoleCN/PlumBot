package me.regadpole.plumbot.listener.qq

import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.notice.OneBotGroupMemberDecreaseEvent


class QQListener {
    companion object {
        fun onGroupMessage(event: OneBotGroupMessageEvent) {
        }

        fun onPrivateMessage(event: OneBotPrivateMessageEvent) {

        }

        /**
         * unstable
         */
        fun onGroupMemberDecreased(event: OneBotGroupMemberDecreaseEvent) {}
    }
}