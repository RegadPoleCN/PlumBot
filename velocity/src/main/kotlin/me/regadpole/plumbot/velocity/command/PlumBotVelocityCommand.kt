// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.velocity.command

import com.velocitypowered.api.command.SimpleCommand
import me.regadpole.plumbot.command.CommandHost
import me.regadpole.plumbot.command.CommandOutput
import me.regadpole.plumbot.command.PlumBotCommandHandler
import me.regadpole.plumbot.command.RenderCommandService
import me.regadpole.plumbot.velocity.PlumBotVelocity
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Velocity 适配层：只做权限判定，[Component] 直接交给代理端发送，无需重定位。
 */
class PlumBotVelocityCommand(private val plugin: PlumBotVelocity) : SimpleCommand {

    private val handler = PlumBotCommandHandler(object : CommandHost {
        override val displayName: String = "PlumBot Velocity"
        override fun reloadPlugin() = plugin.reloadPlugin()
        override fun onlinePlayerCount(): Int = plugin.listPlayers().size
        override fun kickPlayer(playerName: String) = plugin.kickPlayer(playerName)
    }, RenderCommandService(plugin.platform), { plugin.platform.echoDiagnostics() })

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
