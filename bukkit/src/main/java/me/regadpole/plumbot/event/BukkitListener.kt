package me.regadpole.plumbot.event

import me.regadpole.plumbot.event.impl.BukkitJoinEvent
import me.regadpole.plumbot.listener.game.GameListener
import me.regadpole.plumbot.util.impl.BukkitPlayer
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import taboolib.common.platform.event.SubscribeEvent

class BukkitListener {
    @SubscribeEvent
    fun onJoin(event: PlayerJoinEvent) {
        GameListener.onJoin(BukkitJoinEvent(event.player))
    }

    @SubscribeEvent
    fun onQuit(event: PlayerQuitEvent) {
        GameListener.onQuit(GQuitEvent(BukkitPlayer(event.player)))
    }

    @SubscribeEvent
    fun onChat(event: AsyncPlayerChatEvent) {
        GameListener.onChat(GPlayerChatEvent(event.message, BukkitPlayer(event.player)))
    }

    @SubscribeEvent
    fun onDeath(event: PlayerDeathEvent) {
        GameListener.onDie(GDeathEvent(BukkitPlayer(event.entity)))
    }
}