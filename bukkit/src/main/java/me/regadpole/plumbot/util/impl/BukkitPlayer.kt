package me.regadpole.plumbot.util.impl;

import me.regadpole.plumbot.util.GPlayer
import me.regadpole.plumbot.util.GPosition
import org.bukkit.entity.Player

class BukkitPlayer(private val player: Player) : GPlayer(player.name,
    GPosition(player.world.name, player.location.x, player.location.y, player.location.z, player.location.yaw, player.location.pitch),
    player.server.name, player.isOp) {

    override fun teleport() {
        player.teleport(player.location)
    }

    override fun sendMessage(message: String) {
        player.sendMessage(message)
    }
}
