package me.regadpole.plumbot.bukkit.listener

import me.dreamvoid.miraimc.bukkit.event.group.member.MiraiMemberLeaveEvent
import me.dreamvoid.miraimc.bukkit.event.message.passive.MiraiGroupMessageEvent
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.bot.BotProvider
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

class MiraiMCListener(private val plugin: PlumBot): Listener {
    @EventHandler
    fun onGroupMessage(event: MiraiGroupMessageEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return
        plugin.submitAsync {
            BotProvider.getBot()?.handler?.onGroupMessage(event.message, event.groupID, event.senderID)
        }
    }

    @EventHandler
    fun onGroupDecrease(event: MiraiMemberLeaveEvent) {
        if (!plugin.config.getLongList("groups").contains(event.groupID)) return
        plugin.submitAsync {
            BotProvider.getBot()?.handler?.onUserDecrease(event.groupID, event.targetID)
        }
    }
}