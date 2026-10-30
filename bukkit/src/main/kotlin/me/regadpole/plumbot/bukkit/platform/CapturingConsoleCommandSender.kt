// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.utils.OutputCapturingBuffer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import org.bukkit.command.ConsoleCommandSender

/**
 * 远程控制台代理 Sender，委托给通用的 [OutputCapturingBuffer]，覆盖所有消息发送变体，确保零遗漏。
 */
class CapturingConsoleCommandSender(
    private val delegate: ConsoleCommandSender,
    private val capturer: OutputCapturingBuffer = OutputCapturingBuffer(delegate as? Audience)
) : ConsoleCommandSender by delegate, Audience by capturer {

    override fun sendMessage(message: String) {
        capturer.appendString(message)
        delegate.sendMessage(message)
    }

    override fun sendMessage(messages: Array<out String>) {
        for (msg in messages) {
            sendMessage(msg)
        }
    }

    override fun sendMessage(message: Component) {
        capturer.sendMessage(message)
    }

    override fun sendMessage(message: ComponentLike) {
        capturer.sendMessage(message)
    }

    fun getOutput(): String = capturer.getResult()
}
