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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.internal.LogLevel

class RemoteConsoleCommandService(private val service: BotCommandService) {

    fun execute(command: String, groupId: Long, userId: Long) {
        val remoteConsoleEnabled = service.context.config.getBoolean("feature", "remote_console", "enable")
        if (!remoteConsoleEnabled) {
            service.sendRawMessage(groupId, Messages.remoteCommandDisabled)
            return
        }

        val admins = service.context.config.getLongList("admins")
        if (userId !in admins) {
            service.context.log(
                LogLevel.WARN,
                "[PlumBot-Security] 非管理员 QQ ($userId) 尝试在群 ($groupId) 执行远程指令: $command (已拦截)"
            )
            service.sendRawMessage(groupId, Messages.remoteCommandDenied)
            return
        }

        if (!service.context.hasCapability(PlatformCapability.COMMAND_DISPATCH)) {
            service.sendRawMessage(groupId, "当前平台不支持远程指令调度执行。")
            return
        }

        val trimmedCmd = command.trim().removePrefix("/")
        if (trimmedCmd.isBlank()) {
            service.sendRawMessage(groupId, "请输入有效的控制台指令内容！")
            return
        }

        val maxLength = service.context.config.getInteger("feature", "remote_console", "max_output_length")
            .let { if (it <= 0) 1500 else it }

        service.context.dispatchConsoleCommand(trimmedCmd).thenAccept { rawOutput ->
            val truncatedOutput = if (rawOutput.length > maxLength) {
                rawOutput.substring(0, maxLength) + "\n... (输出已截断)"
            } else {
                rawOutput
            }

            val rendered = service.render(
                Messages.remoteCommandExecuted,
                "%output%" to truncatedOutput
            )
            service.sendRawMessage(groupId, rendered)
        }.exceptionally { err ->
            service.sendRawMessage(groupId, "执行指令时异常: ${err.message}")
            null
        }
    }
}
