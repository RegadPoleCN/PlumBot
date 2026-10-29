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

import me.regadpole.plumbot.bot.command.MessageModeResolver
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.filter.FilterManagerHolder
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.utils.stripMinecraftFormatting
import me.regadpole.plumbot.render.RenderRoute
import java.util.UUID

/**
 * 负责统一桥接游戏内发生的所有事件（玩家聊天、进服、离服、遇难、成就）至 QQ 群。
 */
class GameEventBridge(
    private val context: PlatformContext,
    private val sender: ServerMessageSender = ServerMessageSender(context)
) {
    private val config get() = context.config

    fun onChat(playerName: String, serverName: String, rawMessage: String) {
        if (!config.getBoolean("feature", "message", "enable")) return
        if (!config.getBoolean("feature", "message", "to_group")) return

        val cleanMessage = rawMessage.stripMinecraftFormatting()

        val filterResult = FilterManagerHolder.manager?.process(cleanMessage)
        if (filterResult?.isBlocked == true) return
        val messageToSend = filterResult?.sanitizedText ?: cleanMessage

        val mode = config.getInteger("feature", "message", "mode")
        val prefix = config.getString("feature", "message", "prefix")
        val resolved = MessageModeResolver.resolve(messageToSend, mode, prefix) ?: return

        val rendered = Messages.server2ob
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%message%", resolved)
        sender.broadcastRendered(
            route = RenderRoute.MESSAGE,
            card = RenderCards.message(rendered, playerName, serverName, resolved),
            pic = true
        )
    }

    fun onJoin(playerName: String) {
        // 与广播开关无关：进服即补写绑定记录的 UUID
        persistPlayerUuid(playerName)

        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "joinProxy")) return

        val rendered = Messages.joinProxy.replace("%player_name%", playerName)
        sender.broadcastRendered(
            route = RenderRoute.JOIN_AND_LEAVE,
            card = RenderCards.joinAndLeave(rendered, "join", playerName, null),
            pic = true
        )
    }

    /**
     * 玩家进服时把平台 UUID 补写进绑定表：此前只有第三方 API 会写，内部流程从不写，
     * 导致 `getUUIDByName` / `getUUIDByUser` 恒返回 null；进服是同时知道名字与 UUID 的时刻。
     */
    private fun persistPlayerUuid(playerName: String) {
        runCatching {
            val database = DatabaseProvider.getDatabase() ?: return
            val rawUuid = context.playerService.getPlayerUuid(playerName) ?: return
            val uuid = runCatching { UUID.fromString(rawUuid) }.getOrNull() ?: return
            val binding = database.getByName(playerName) ?: return
            if (binding.playerUUID == uuid.toString()) return
            database.setUUID(playerName, uuid)
        }.onFailure {
            context.log(LogLevel.WARN, "[PlumBot] 补写玩家 UUID 失败 ($playerName): ${it.message}")
        }
    }

    fun onLeave(playerName: String, serverName: String) {
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "leaveProxy")) return

        val rendered = Messages.leaveProxy
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
        sender.broadcastRendered(
            route = RenderRoute.JOIN_AND_LEAVE,
            card = RenderCards.joinAndLeave(rendered, "leave", playerName, serverName),
            pic = true
        )
    }

    fun onChangeServer(playerName: String, originServer: String, targetServer: String) {
        if (!context.hasCapability(PlatformCapability.SERVER_SWITCH_BROADCAST)) return
        val totalEnabled = config.getBoolean("feature", "joinAndLeave", "enable")
        if (!totalEnabled || !config.getBoolean("feature", "joinAndLeave", "changeServer")) return

        val rendered = Messages.changeServer
            .replace("%player_name%", playerName)
            .replace("%origin_server%", originServer)
            .replace("%target_server%", targetServer)
        sender.broadcastRendered(
            route = RenderRoute.CHANGE_SERVER,
            card = RenderCards.changeServer(rendered, playerName, originServer, targetServer),
            pic = true
        )
    }

    fun onDeath(playerName: String, serverName: String, deathMessage: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_DEATH_BROADCAST)) return
        if (!config.getBoolean("feature", "death", "enable")) return

        val rendered = Messages.death
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%death_message%", deathMessage)
        sender.broadcastRendered(
            route = RenderRoute.DEATH,
            card = RenderCards.death(rendered, playerName, serverName, deathMessage),
            pic = true
        )
    }

    fun onAdvancement(playerName: String, serverName: String, advancementTitle: String) {
        if (!context.hasCapability(PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST)) return
        if (!config.getBoolean("feature", "advancement", "enable")) return

        val rendered = Messages.advancement
            .replace("%server%", serverName)
            .replace("%player_name%", playerName)
            .replace("%advancement_title%", advancementTitle)
        sender.broadcastRendered(
            route = RenderRoute.ADVANCEMENT,
            card = RenderCards.advancement(rendered, playerName, serverName, advancementTitle),
            pic = true
        )
    }
}
