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

package me.regadpole.plumbot.hytale.command

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.AbstractCommand
import com.hypixel.hytale.server.core.command.system.CommandContext
import me.regadpole.plumbot.command.CommandHost
import me.regadpole.plumbot.command.CommandOutput
import me.regadpole.plumbot.command.PlumBotCommandHandler
import me.regadpole.plumbot.hytale.PlumBotHytale
import me.regadpole.plumbot.utils.toPlainText
import java.util.concurrent.CompletableFuture

/**
 * Hytale 适配层：只做参数拆分、权限（框架侧 `requirePermission`）与
 * `Component → 纯文本` 落地。命令语义统一在 common 的 [PlumBotCommandHandler]。
 *
 * Hytale 宿主不带 Adventure，纯文本转换走 [toPlainText]（只递归 TextComponent，不需要额外模块）。
 */
class HytalePlumBotCommand(private val plugin: PlumBotHytale) :
    AbstractCommand("plumbot", "PlumBot 管理命令") {

    private val handler = PlumBotCommandHandler(object : CommandHost {
        override val displayName: String = "PlumBot Hytale"
        override fun reloadPlugin() = plugin.reloadPlugin()
        override fun onlinePlayerCount(): Int = plugin.listPlayers().size
        override fun kickPlayer(playerName: String) = plugin.kickPlayer(playerName)
    })

    init {
        requirePermission("plumbot.admin")
        addAliases("pb")
        setAllowsExtraArguments(true)
    }

    override fun execute(context: CommandContext): CompletableFuture<Void> {
        val parts = context.inputString.trim().split("\\s+".toRegex()).drop(1)
        val output = CommandOutput { message -> context.sendMessage(Message.raw(message.toPlainText())) }
        handler.execute(output, parts)
        return CompletableFuture.completedFuture(null)
    }
}
