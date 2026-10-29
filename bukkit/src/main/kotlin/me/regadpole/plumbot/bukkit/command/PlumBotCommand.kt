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

package me.regadpole.plumbot.bukkit.command

import me.regadpole.plumbot.bukkit.PlumBotBukkit
import me.regadpole.plumbot.command.CommandHost
import me.regadpole.plumbot.command.CommandOutput
import me.regadpole.plumbot.command.PlumBotCommandHandler
import me.regadpole.plumbot.command.RenderCommandService
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Bukkit 适配层：只做权限判定、Tab 补全与 `Component → § 传统文本` 落地；命令语义在 [PlumBotCommandHandler]。
 */
class PlumBotCommand(private val plugin: PlumBotBukkit) : CommandExecutor, TabCompleter {

    private val handler = PlumBotCommandHandler(object : CommandHost {
        override val displayName: String = "PlumBot"
        override fun reloadPlugin() = plugin.reloadPlugin()
        override fun onlinePlayerCount(): Int = plugin.listPlayers().size
        override fun kickPlayer(playerName: String) = plugin.kickPlayer(playerName)
    }, RenderCommandService(plugin.platform), { plugin.platform.echoDiagnostics() })

    private val legacy: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("plumbot.admin")) {
            sender.sendMessage(legacy.serialize(Component.text("你没有权限执行此命令。", NamedTextColor.RED)))
            return true
        }

        handler.execute(CommandOutput { sender.sendMessage(legacy.serialize(it)) }, args.toList())
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): List<String> {
        if (!sender.hasPermission("plumbot.admin")) return emptyList()
        return handler.suggest(args.toList())
    }
}
