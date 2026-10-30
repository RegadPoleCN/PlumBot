// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.listener

import com.hypixel.hytale.event.EventRegistry
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService

class HytaleServerListener(
    private val plugin: PlumBot,
    private val eventRegistry: EventRegistry,
    private val eventBridge: GameEventBridge,
    private val loginService: PlayerLoginService
) {

    fun register() {
        // 1. 最前置握手白名单拦截：在区块和实体加载前直接阻断
        eventRegistry.register(PlayerSetupConnectEvent::class.java) { event ->
            val result = loginService.check(event.username)
            if (!result.allowed) {
                event.isCancelled = true
                event.reason = Message.raw(result.kickMessage ?: "未在白名单内")
            }
        }

        // 2. 玩家在世界完全就绪后，才触发进服群广播：防假人和未通过白名单者刷屏
        eventRegistry.register(PlayerReadyEvent::class.java) { event ->
            val playerName = event.player.playerRef.username
            plugin.platform.scheduler.runAsync {
                eventBridge.onJoin(playerName)
            }
        }

        // 3. 聊天消息异步脱敏转发
        eventRegistry.register(PlayerChatEvent::class.java) { event ->
            val sender = event.sender.username
            val message = event.content
            plugin.platform.scheduler.runAsync {
                eventBridge.onChat(sender, "Hytale", message)
            }
        }

        // 4. 离服广播
        eventRegistry.register(PlayerDisconnectEvent::class.java) { event ->
            val username = event.playerRef.username
            plugin.platform.scheduler.runAsync {
                eventBridge.onLeave(username, "Hytale")
            }
        }
    }
}
