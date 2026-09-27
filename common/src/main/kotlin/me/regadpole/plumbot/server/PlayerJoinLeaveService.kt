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

package me.regadpole.plumbot.server

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.config.YamlConfigurator

object PlayerJoinLeaveService {
    fun notifyJoin(playerName: String, config: YamlConfigurator) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled) return
        if (!config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        val rendered = Messages.joinProxy
            .replace("%player_name%", playerName)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "joinAndLeave", "pic")
        )
    }

    fun notifyLeave(playerName: String, serverName: String, config: YamlConfigurator) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled) return
        if (!config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        val rendered = Messages.leaveProxy
            .replace("%player_name%", playerName)
            .replace("%server%", serverName)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "joinAndLeave", "pic")
        )
    }
}
