// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
