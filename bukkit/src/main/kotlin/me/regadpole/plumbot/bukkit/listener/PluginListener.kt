/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
