// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.velocity.platform

import com.velocitypowered.api.plugin.PluginContainer
import me.regadpole.plumbot.api.Plugin
import kotlin.jvm.optionals.getOrElse

/**
 * 包装 Velocity 原生 [PluginContainer] 为平台中立的 [me.regadpole.plumbot.api.Plugin] 凭据。
 */
class VelocityPlugin(
    private val container: PluginContainer
) : Plugin {
    override val name: String get() = container.description.id
    override val version: String get() = container.description.version.getOrElse { "unknown" }
    override val isEnabled: Boolean get() = true

    override fun equals(other: Any?): Boolean =
        other is Plugin && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase().hashCode()

    override fun toString(): String = "VelocityPlugin(name=$name, version=$version)"
}
