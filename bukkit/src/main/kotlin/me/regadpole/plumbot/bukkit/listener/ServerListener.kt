package me.regadpole.plumbot.bukkit.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.server.PlayerJoinLeaveService
import me.regadpole.plumbot.server.PlayerLoginService
import me.regadpole.plumbot.server.ServerChatService
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent

class ServerListener(private val plugin: PlumBot): Listener {
    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (event.isCancelled) return

        ServerChatService(plugin.config).handleChat(
            event.player.name,
            Bukkit.getServer().name,
            event.message
        )
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return

        val result = PlayerLoginService(plugin.config).check(event.name)
        if (result.allowed) {
            event.allow()
        } else {
            event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                result.kickMessage
            )
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        val playerName = event.player.name
        val serverName = Bukkit.getServer().name
        plugin.submitAsync {
            PlayerJoinLeaveService(plugin.config).notifyLeave(playerName, serverName)
        }
    }
}
