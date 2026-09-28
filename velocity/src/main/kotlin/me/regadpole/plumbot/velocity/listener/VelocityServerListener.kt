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

package me.regadpole.plumbot.velocity.listener

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.player.PlayerChatEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.proxy.Player
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService
import me.regadpole.plumbot.utils.asMiniMessage

class VelocityServerListener(
    private val platform: PlatformContext,
    private val eventBridge: GameEventBridge,
    private val loginService: PlayerLoginService
) {

    private fun resolveServerName(player: Player? = null): String {
        val configured = platform.config.getString("server", "name")
        if (!configured.isNullOrBlank()) return configured
        return player?.currentServer?.map { it.serverInfo.name }?.orElse("Proxy") ?: "Proxy"
    }

    @Subscribe(order = PostOrder.FIRST)
    fun onPreLogin(event: PreLoginEvent) {
        val result = loginService.check(event.username)
        if (!result.allowed) {
            val kickReason = (result.kickMessage ?: "Not in whitelist").asMiniMessage()
            event.result = PreLoginEvent.PreLoginComponentResult.denied(kickReason)
        }
    }

    @Subscribe
    fun onChat(event: PlayerChatEvent) {
        val player = event.player
        val serverName = resolveServerName(player)
        val message = event.message

        // 切换到异步调度器执行，防阻塞 Velocity Netty 事件循环
        platform.scheduler.runAsync {
            eventBridge.onChat(player.username, serverName, message)
        }
    }

    @Subscribe
    fun onServerConnected(event: ServerConnectedEvent) {
        val player = event.player
        val previousServer = event.previousServer.map { it.serverInfo.name }.orElse(null)
        val currentServer = event.server.serverInfo.name

        platform.scheduler.runAsync {
            if (previousServer == null) {
                // 初次进入代理端网络
                eventBridge.onJoin(player.username)
            } else {
                // 子服间跨服跳转
                eventBridge.onChangeServer(player.username, previousServer, currentServer)
            }
        }
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        val serverName = resolveServerName(player)

        platform.scheduler.runAsync {
            eventBridge.onLeave(player.username, serverName)
        }
    }
}
