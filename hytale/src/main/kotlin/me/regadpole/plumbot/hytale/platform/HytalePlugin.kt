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
