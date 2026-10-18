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

package me.regadpole.plumbot.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import me.regadpole.plumbot.command.CommandHost
import me.regadpole.plumbot.command.CommandOutput
import me.regadpole.plumbot.command.PlumBotCommandHandler
import me.regadpole.plumbot.velocity.PlumBotVelocity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Velocity 适配层：只做权限判定 + 把 common 生成的 [Component] 直接交给代理端发送。
 * Velocity 自带 Adventure，因此这里可以直接使用宿主类，无需重定位或回退装载。
 */
class PlumBotVelocityCommand(private val plugin: PlumBotVelocity) : SimpleCommand {

    private val handler = PlumBotCommandHandler(object : CommandHost {
        override val displayName: String = "PlumBot Velocity"
        override fun reloadPlugin() = plugin.reloadPlugin()
        override fun onlinePlayerCount(): Int = plugin.listPlayers().size
        override fun kickPlayer(playerName: String) = plugin.kickPlayer(playerName)
    })

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!hasPermission(invocation)) {
            source.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED))
            return
        }

        handler.execute(CommandOutput { source.sendMessage(it) }, invocation.arguments().toList())
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> =
        handler.suggest(invocation.arguments().toList())

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
        invocation.source().hasPermission("plumbot.admin")
}
