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

package me.regadpole.plumbot.velocity.platform

import com.velocitypowered.api.plugin.PluginContainer
import me.regadpole.plumbot.api.Plugin

/**
 * 包装 Velocity 原生 [PluginContainer] 为平台中立的 [me.regadpole.plumbot.api.Plugin] 凭据。
 */
class VelocityPlugin(
    private val container: PluginContainer
) : Plugin {
    override val name: String get() = container.description.id
    override val version: String get() = container.description.version.orElse("unknown")
    override val isEnabled: Boolean get() = true

    override fun equals(other: Any?): Boolean =
        other is Plugin && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = "VelocityPlugin(name=$name, version=$version)"
}
