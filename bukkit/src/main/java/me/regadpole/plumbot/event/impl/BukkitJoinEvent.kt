package me.regadpole.plumbot.event.impl

import me.regadpole.plumbot.event.GJoinEvent
import me.regadpole.plumbot.util.impl.BukkitPlayer
import org.bukkit.entity.Player

class BukkitJoinEvent(private val bukkitPlayer: Player) : GJoinEvent(BukkitPlayer(bukkitPlayer)) {
    override fun kick(message: String) {
        bukkitPlayer.kickPlayer(message)
    }
}