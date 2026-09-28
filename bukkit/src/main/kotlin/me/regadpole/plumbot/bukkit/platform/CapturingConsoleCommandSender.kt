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

package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.utils.OutputCapturingBuffer
import net.kyori.adventure.audience.Audience
import org.bukkit.command.ConsoleCommandSender

/**
 * 远程控制台代理 Sender，委托给通用的 [OutputCapturingBuffer]，双通道捕获 String 与 Adventure Component 输出。
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

    fun getOutput(): String = capturer.getResult()
}
