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
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ServerListener(private val plugin: PlumBot): Listener {

    private fun resolveServerName(): String {
        val configured = plugin.config.getString("server", "name")
        return if (!configured.isNullOrBlank()) configured else Bukkit.getServer().name
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (event.isCancelled) return

        ServerChatService(plugin.config).handleChat(
            event.player.name,
            resolveServerName(),
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
    fun onJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        plugin.submitAsync {
            PlayerJoinLeaveService.notifyJoin(playerName, plugin.config)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        val playerName = event.player.name
        val serverName = resolveServerName()
        plugin.submitAsync {
            PlayerJoinLeaveService.notifyLeave(playerName, serverName, plugin.config)
        }
    }
}
