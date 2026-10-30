// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import me.regadpole.plumbot.api.Plugin

class HytalePlugin(private val plugin: JavaPlugin) : Plugin {
    override val name: String get() = plugin.manifest.name
    override val version: String get() = plugin.manifest.version.toString()
    override val isEnabled: Boolean get() = plugin.isEnabled

    override fun equals(other: Any?): Boolean =
        other is Plugin && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase().hashCode()
    override fun toString(): String = "HytalePlugin(name=$name, version=$version)"
}
