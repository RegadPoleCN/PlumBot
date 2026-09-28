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

import com.velocitypowered.api.proxy.ProxyServer
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.utils.asMiniMessage

class VelocityPlayerService(private val server: ProxyServer) : PlatformPlayerService {

    override fun kickPlayer(name: String) {
        val player = server.getPlayer(name).orElse(null) ?: return
        player.disconnect("您已被移出服务器。".asMiniMessage())
    }

    override fun listPlayers(): List<String> =
        server.allPlayers.map { it.username }

    override fun listPlayerString(): String =
        listPlayers().joinToString(", ")
}
