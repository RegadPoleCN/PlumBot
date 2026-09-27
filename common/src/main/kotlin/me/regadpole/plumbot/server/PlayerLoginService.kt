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
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent

class PlayerLoginService(
    private val context: PlatformContext,
    private val sender: ServerMessageSender = ServerMessageSender(context)
) {

    private val config get() = context.config

    fun check(playerName: String): PreLoginResult {
        if (config.getBoolean("feature", "bind", "whitelist")) {
            val qq = DatabaseProvider.getBindByName(playerName)
            if (qq.isNullOrEmpty()) {
                notifyKick(playerName)
                return PreLoginResult(
                    allowed = false,
                    kickMessage = getLegacyFromComponent(
                        getComponentFromMiniMsg(
                            Messages.kickServer
                                .replace("%groups%", config.getLongList("groups").toString())
                        )
                    )
                )
            }
            return PreLoginResult(allowed = true)
        }
        return PreLoginResult(allowed = true)
    }

    private fun notifyKick(playerName: String) {
        val rendered = Messages.kickPlatform.replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "bind", "pic"))
    }
}