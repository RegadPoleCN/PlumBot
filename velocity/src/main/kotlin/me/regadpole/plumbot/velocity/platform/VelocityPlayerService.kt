// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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

    override fun getPlayerUuid(name: String): String? =
        server.getPlayer(name).orElse(null)?.uniqueId?.toString()

    /**
     * ② 内存 GameProfile 的纹理属性。
     *
     * Velocity 的 profile 由 Mojang 签发（在线模式）或由登录插件注入，
     * `textures` 属性即 base64 皮肤数据 —— 无需任何网络请求。
     */
    override fun skinTextureFromProfile(playerName: String): String? = runCatching {
        val player = server.getPlayer(playerName).orElse(null) ?: return null
        player.gameProfile.properties
            .firstOrNull { it.name.equals("textures", ignoreCase = true) }
            ?.value
    }.getOrNull()

    /**
     * ③ 正版 UUID：仅在线模式下 profile 才是 Mojang 签发的（离线模式为离线 UUID，
     * 拿去问会话服务只会白费一次请求）。
     */
    override fun premiumUuid(playerName: String): String? = runCatching {
        if (!server.configuration.isOnlineMode) return null
        server.getPlayer(playerName).orElse(null)?.uniqueId?.toString()
    }.getOrNull()
}
