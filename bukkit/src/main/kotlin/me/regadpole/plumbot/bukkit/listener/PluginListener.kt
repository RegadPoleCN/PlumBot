package me.regadpole.plumbot.bukkit.listener

import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bukkit.platform.BukkitPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent

/**
 * Auto-cleanup listener for third-party bot adapters and event subscriptions.
 *
 * When a third-party plugin unregisters (Bukkit fires [PluginDisableEvent]):
 * 1. Any [me.regadpole.plumbot.bot.BotFactory] it contributed is removed.
 * 2. Any event listener subscribed via [PlumBotAPI.subscribeGroupMessage] with plugin ownership
 *    is deterministically closed, eliminating Metaspace memory leaks.
 */
class PluginListener : Listener {

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val plugin = BukkitPlugin(event.plugin)
        val api = runCatching { PlumBotAPI.get() }.getOrNull()
        if (api != null) {
            val removedFactories = api.extensionRegistry.unregisterAllFor(plugin)
            if (removedFactories > 0) {
                event.plugin.logger.info(
                    "[PlumBot] Unregistered $removedFactories external bot factories on PluginDisable"
                )
            }
        }
        BotEventDispatcher.unregisterAllFor(plugin)
    }
}
