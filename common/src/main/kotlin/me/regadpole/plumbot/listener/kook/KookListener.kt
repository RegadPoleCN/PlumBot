package me.regadpole.plumbot.listener.kook

import me.regadpole.plumbot.listener.GPlatformListener
import snw.jkook.event.EventHandler
import snw.jkook.event.Listener
import snw.jkook.event.channel.ChannelMessageEvent
import snw.jkook.event.pm.PrivateMessageReceivedEvent
import snw.jkook.event.user.UserLeaveGuildEvent

class KookListener : Listener {
    @EventHandler
    fun onChannelMessage(event: ChannelMessageEvent) {
        GPlatformListener.onGroupMessage(event.message.component.toString(),
            event.channel.id, event.message.sender.id, event.message.sender.name)
    }

    @EventHandler
    fun onPrivateMessage(event: PrivateMessageReceivedEvent) {

    }

    @EventHandler
    fun onPrivateMessage(event: UserLeaveGuildEvent) {

    }
}