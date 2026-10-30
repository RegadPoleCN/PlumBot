// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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

    override fun equals(other: Any?): Boolean =
        other is Plugin && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = "BukkitPlugin(name=$name, version=$version)"
}
