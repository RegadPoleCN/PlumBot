// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.server.GameEventBridge
import me.regadpole.plumbot.server.PlayerLoginService
import me.regadpole.plumbot.server.PreLoginDenial
import me.regadpole.plumbot.utils.getPlainTextFromComponent
import me.regadpole.plumbot.utils.stripMinecraftFormatting
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class ServerListener(private val plugin: PlumBot) : Listener {

    private val messageSender = me.regadpole.plumbot.server.ServerMessageSender(plugin.platform)
    private val bridge = GameEventBridge(plugin.platform, messageSender)
    private val loginService = PlayerLoginService(plugin.platform, messageSender)

    private fun resolveServerName(): String {
        val configured = plugin.config.getString("server", "name")
        return if (!configured.isNullOrBlank()) configured else Bukkit.getServer().name
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        if (event.isCancelled) return
        bridge.onChat(
            event.player.name,
            resolveServerName(),
            event.message
        )
    }

    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return

        val result = loginService.check(event.name)
        if (result.allowed) {
            event.allow()
        } else {
            // 查清「没绑定」= 白名单未通过；「没查清」（超时/库未就绪）= 校验通道故障，
            // 用 KICK_OTHER 区分，免得运维把数据库故障误读成玩家没申请白名单。
            val reason = if (result.denial == PreLoginDenial.LOOKUP_UNAVAILABLE) {
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER
            } else {
                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST
            }
            event.disallow(reason, result.kickMessage)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        plugin.platform.scheduler.runAsync {
            bridge.onJoin(playerName)
        }
    }

    @EventHandler
    fun onLeave(event: PlayerQuitEvent) {
        val playerName = event.player.name
        val serverName = resolveServerName()
        plugin.platform.scheduler.runAsync {
            bridge.onLeave(playerName, serverName)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        if (!plugin.platform.hasCapability(PlatformCapability.PLAYER_DEATH_BROADCAST)) return
        val playerName = event.entity.name
        val deathMessage = event.deathMessage ?: return
        val cleanMessage = deathMessage.stripMinecraftFormatting()
        val serverName = resolveServerName()

        plugin.platform.scheduler.runAsync {
            bridge.onDeath(playerName, serverName, cleanMessage)
        }
    }

    @EventHandler
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!plugin.platform.hasCapability(PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST)) return

        val advancement = event.advancement
        val key = advancement.key.key
        // 配方解锁随进度大量刷出，不是成就播报内容。
        if (key.startsWith("recipes/")) return

        // display 只有 Paper 提供，用反射兼容不带该 API 的宿主。
        val titleText: String = try {
            val displayMethod = advancement.javaClass.methods.firstOrNull { it.name == "getDisplay" || it.name == "display" }
            val displayObj = displayMethod?.invoke(advancement)
            if (displayObj != null) {
                val titleMethod = displayObj.javaClass.methods.firstOrNull { it.name == "getTitle" || it.name == "title" }
                when (val titleObj = titleMethod?.invoke(displayObj)) {
                    is net.kyori.adventure.text.Component -> getPlainTextFromComponent(titleObj)
                    is String -> titleObj
                    else -> titleObj?.toString() ?: ""
                }
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        }.ifBlank {
            // 回退使用键名格式化：如 "story/mine_stone" -> "mine_stone"
            key.substringAfterLast('/')
        }

        if (titleText.isBlank()) return

        val playerName = event.player.name
        val serverName = resolveServerName()

        plugin.platform.scheduler.runAsync {
            bridge.onAdvancement(playerName, serverName, titleText)
        }
    }
}
