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

import com.velocitypowered.api.proxy.ConsoleCommandSource
import me.regadpole.plumbot.utils.OutputCapturingBuffer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.MessageType
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.permission.PermissionChecker
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

class CapturingVelocityCommandSource(
    private val delegate: ConsoleCommandSource,
    private val capturer: OutputCapturingBuffer = OutputCapturingBuffer(delegate)
) : ConsoleCommandSource by delegate, Audience by capturer {

    override fun sendMessage(message: Component) {
        capturer.sendMessage(message)
    }

    override fun sendMessage(message: ComponentLike) {
        capturer.sendMessage(message)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun sendMessage(message: Component, type: MessageType) {
        capturer.sendMessage(message)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun sendMessage(message: ComponentLike, type: MessageType) {
        capturer.sendMessage(message)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun sendMessage(source: Identity, message: Component, type: MessageType) {
        capturer.sendMessage(message)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun sendMessage(source: Identity, message: ComponentLike, type: MessageType) {
        capturer.sendMessage(message)
    }

    override fun sendPlainMessage(message: String) {
        capturer.appendString(message)
        delegate.sendPlainMessage(message)
    }

    override fun sendRichMessage(message: String) {
        val component = MiniMessage.miniMessage().deserialize(message)
        capturer.sendMessage(component)
        delegate.sendMessage(component)
    }

    override fun sendRichMessage(message: String, vararg resolvers: TagResolver) {
        val component = MiniMessage.miniMessage().deserialize(message, *resolvers)
        capturer.sendMessage(component)
        delegate.sendMessage(component)
    }

    fun getOutput(): String = capturer.getResult()

    override fun hasPermission(permission: String?): Boolean {
        return true
    }

    override fun getPermissionChecker(): PermissionChecker? {
        return delegate.permissionChecker
    }
}
