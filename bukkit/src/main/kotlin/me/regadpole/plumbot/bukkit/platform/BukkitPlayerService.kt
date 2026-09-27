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

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import org.bukkit.Server

private const val PLAYERS_PER_LINE = 5

class BukkitPlayerService(
    private val server: Server,
    private val configProvider: () -> YamlConfigurator,
): PlatformPlayerService {
    override fun kickPlayer(name: String) {
        val kickMessage = getLegacyFromComponent(getComponentFromMiniMsg(
            Messages.kickServer
                .replace("%groups%", configProvider().getLongList("groups").toString())
        ))
        server.getPlayer(name)?.kickPlayer(kickMessage)
    }

    override fun listPlayers(): List<String> {
        return server.onlinePlayers
            .map { it.name }
            .sorted()
            .toList()
    }

    override fun listPlayerString(): String {
        val playersPerLine = PLAYERS_PER_LINE
        var list = server.onlinePlayers.map { it.name }.sorted().toList()
        var result = ""
        while(list.size > playersPerLine) {
            result += list.slice(0..<playersPerLine).joinToString(postfix = "\n  ")
            list = list.drop(playersPerLine)
        }
        result += list.joinToString()
        result += "\n"
        return result
    }
}
