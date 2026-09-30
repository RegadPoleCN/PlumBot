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

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.api.platform.PlatformPlayerService

class HytalePlayerService : PlatformPlayerService {

    override fun kickPlayer(name: String) {
        val player = Universe.get().getPlayerByUsername(name, NameMatching.EXACT) ?: return
        player.packetHandler.disconnect(Message.raw("您已被移出服务器。"))
    }

    override fun listPlayers(): List<String> =
        Universe.get().players.map { it.username }

    override fun listPlayerString(): String =
        listPlayers().joinToString(", ")
}
