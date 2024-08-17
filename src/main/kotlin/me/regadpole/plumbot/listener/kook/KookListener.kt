package me.regadpole.plumbot.listener.kook

import snw.jkook.event.EventHandler
import snw.jkook.event.Listener
import snw.jkook.event.channel.ChannelMessageEvent
import snw.jkook.event.pm.PrivateMessageEvent
import snw.jkook.event.user.UserLeaveGuildEvent

class KookListener : Listener {
    @EventHandler
    fun onChannelMessage(e: ChannelMessageEvent) {

    }

    @EventHandler
    fun onPrivateMessage(e: PrivateMessageEvent) {

    }

    @EventHandler
    fun onPrivateMessage(e: UserLeaveGuildEvent) {

    }
}