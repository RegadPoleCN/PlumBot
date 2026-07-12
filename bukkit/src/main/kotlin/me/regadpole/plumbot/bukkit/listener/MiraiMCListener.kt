package me.regadpole.plumbot.bukkit.listener

import me.dreamvoid.miraimc.bukkit.event.group.member.MiraiMemberLeaveEvent
import me.dreamvoid.miraimc.bukkit.event.message.passive.MiraiGroupMessageEvent
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.BotEventDispatcher
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class MiraiMCListener(private val plugin: PlumBot): Listener {
    @EventHandler
    fun onGroupMessage(event: MiraiGroupMessageEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val message = event.message
        val groupId = event.groupID
        val senderId = event.senderID
        plugin.submitAsync {
            BotEventDispatcher.dispatchGroupMessage(message, groupId, senderId)
        }
    }

    @EventHandler
    fun onGroupDecrease(event: MiraiMemberLeaveEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return

        val groupId = event.groupID
        val targetId = event.targetID
        plugin.submitAsync {
            BotEventDispatcher.dispatchUserDecrease(groupId, targetId)
        }
    }
}
