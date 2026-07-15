package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.api.Plugin
import org.bukkit.plugin.Plugin as BukkitPluginBase

/**
 * Adapter that exposes a Bukkit [org.bukkit.plugin.Plugin] as the
 * platform-neutral [me.regadpole.plumbot.api.Plugin] used by
 * [me.regadpole.plumbot.bot.BotExtensionRegistry].
 */
class BukkitPlugin(
    private val plugin: BukkitPluginBase,
) : Plugin {
    override val name: String get() = plugin.name
    override val version: String get() = plugin.description.version
    override val isEnabled: Boolean get() = plugin.isEnabled
}
