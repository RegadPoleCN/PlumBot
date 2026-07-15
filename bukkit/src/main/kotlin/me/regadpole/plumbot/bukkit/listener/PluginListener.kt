package me.regadpole.plumbot.bukkit.listener

import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.bukkit.platform.BukkitPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent

/**
 * Auto-cleanup listener for third-party bot adapters.
 *
 * When a third-party plugin unregisters (Bukkit fires [PluginDisableEvent]),
 * any [me.regadpole.plumbot.bot.BotFactory] it had contributed through
 * [me.regadpole.plumbot.bot.BotExtensionRegistry] is removed via
 * [me.regadpole.plumbot.bot.BotExtensionRegistry.unregisterAllFor].
 */
class PluginListener : Listener {

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val api = runCatching { PlumBotAPI.getInstance() }.getOrNull() ?: return
        val ext = runCatching { api.getExtensionRegistry() }.getOrNull() ?: return
        val removed = ext.unregisterAllFor(BukkitPlugin(event.plugin))
        if (removed > 0) {
            event.plugin.logger.info(
                "[PlumBot] Unregistered $removed external bot factories on PluginDisable"
            )
        }
    }
}
