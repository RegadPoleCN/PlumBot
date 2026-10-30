// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandSender
import com.hypixel.hytale.server.core.console.ConsoleSender
import com.hypixel.hytale.server.core.permissions.PermissionQuery
import me.regadpole.plumbot.utils.OutputCapturingBuffer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike

class CapturingHytaleCommandSender(
    private val delegate: CommandSender = ConsoleSender.INSTANCE,
    private val capturer: OutputCapturingBuffer = OutputCapturingBuffer(delegate as? Audience)
) : CommandSender by delegate, Audience by capturer {

    override fun sendMessage(message: Message) {
        val raw = message.rawText ?: message.ansiMessage
        capturer.appendString(raw)
        delegate.sendMessage(message)
    }

    override fun sendMessage(message: Component) {
        capturer.sendMessage(message)
    }

    override fun sendMessage(message: ComponentLike) {
        capturer.sendMessage(message)
    }

    fun getOutput(): String = capturer.getResult()

    /** 不做“成功但无回显”兜底文案的原始捕获，供结构化回显判断是否真的捕获到行。 */
    fun getOutputOrEmpty(): String = capturer.getResult("")

    override fun hasPermission(query: PermissionQuery): Boolean {
        return true
    }

    override fun hasPermission(
        query: PermissionQuery,
        def: Boolean
    ): Boolean {
        return true
    }
}
