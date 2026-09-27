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

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.api.platform.PlatformContext

/**
 * 负责向所有配置互通的群广播消息的发送组件。
 */
class ServerMessageSender(private val context: PlatformContext) {

    fun broadcast(message: String, isPic: Boolean = false) {
        val bot = BotProvider.getBot() ?: return
        context.config.getLongList("groups").forEach { groupId ->
            bot.sendMsg(true, groupId, message, isPic)
        }
    }

    companion object {
        fun broadcastToGroups(
            config: YamlConfigurator,
            message: String,
            isPic: Boolean,
            bot: IBot? = BotProvider.getBot()
        ) {
            val targetBot = bot ?: return
            config.getLongList("groups").forEach { groupId ->
                targetBot.sendMsg(true, groupId, message, isPic)
            }
        }
    }
}
