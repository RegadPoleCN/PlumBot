package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.platform.PlatformMessenger
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin

class BukkitPlatformMessenger(
    private val plugin: Plugin,
): PlatformMessenger {
    private val audience: BukkitAudiences by lazy { BukkitAudiences.create(plugin) }

    override fun sendMessage(message: Component) {
        audience.sender(plugin.server.consoleSender).sendMessage(message)
        audience.players().sendMessage(message)
    }
}
