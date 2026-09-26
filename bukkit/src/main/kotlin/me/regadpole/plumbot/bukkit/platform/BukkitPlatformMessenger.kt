package me.regadpole.plumbot.bukkit.platform

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin

class BukkitPlatformMessenger(
    private val plugin: Plugin,
) {
    private val audience: BukkitAudiences by lazy { BukkitAudiences.create(plugin) }

    fun sendMessage(message: Component) {
        audience.sender(plugin.server.consoleSender).sendMessage(message)
        audience.players().sendMessage(message)
    }
}
