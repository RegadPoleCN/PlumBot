// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent
import org.bukkit.Server

private const val PLAYERS_PER_LINE = 5

class BukkitPlayerService(
    private val server: Server,
    private val configProvider: () -> YamlConfigurator,
): PlatformPlayerService {
    override fun kickPlayer(name: String) {
        val kickMessage = getLegacyFromComponent(getComponentFromMiniMsg(
            Messages.kickServer
                .replace("%groups%", configProvider().getLongList("groups").toString())
        ))
        server.getPlayer(name)?.kickPlayer(kickMessage)
    }

    override fun listPlayers(): List<String> {
        return server.onlinePlayers
            .map { it.name }
            .sorted()
            .toList()
    }

    override fun listPlayerString(): String {
        val playersPerLine = PLAYERS_PER_LINE
        var list = server.onlinePlayers.map { it.name }.sorted().toList()
        var result = ""
        while(list.size > playersPerLine) {
            result += list.slice(0..<playersPerLine).joinToString(postfix = "\n  ")
            list = list.drop(playersPerLine)
        }
        result += list.joinToString()
        result += "\n"
        return result
    }

    override fun getPlayerUuid(name: String): String? =
        server.getPlayer(name)?.uniqueId?.toString()

    /**
     * ① 皮肤插件（SkinsRestorer）的纹理属性 —— **纯反射，零硬依赖**。
     *
     * 目标 API（SkinsRestorer v15+）：
     * `SkinsRestorerProvider.get()` → `getPlayerStorage()` → `getSkinOfPlayer(UUID)` → `getValue()`。
     * 插件缺失或版本 API 不符时返回 null，漏斗自然进入下一级。
     */
    override fun skinTextureFromPlugin(playerName: String): String? = runCatching {
        val uuid = server.getPlayer(playerName)?.uniqueId ?: return null
        val api = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider")
            .getMethod("get")
            .invoke(null) ?: return null
        val storage = api.javaClass.getMethod("getPlayerStorage").invoke(api) ?: return null
        val optional = storage.javaClass
            .getMethod("getSkinOfPlayer", java.util.UUID::class.java)
            .invoke(storage, uuid) as? java.util.Optional<*> ?: return null
        val property = optional.orElse(null) ?: return null
        property.javaClass.getMethod("getValue").invoke(property) as? String
    }.getOrNull()

    /**
     * ② 内存 GameProfile 的纹理属性 —— **反射**调用 Paper 的 `getPlayerProfile()`。
     *
     * 本项目编译目标是 Spigot API（不含 `PlayerProfile`），因此只能用反射：
     * 在 Paper 上可用，在纯 Spigot 上返回 null 并自动落到下一级。
     * 读取 profile 可能触发一次网络请求，调用方已在 `Dispatchers.IO` 中执行。
     */
    override fun skinTextureFromProfile(playerName: String): String? = runCatching {
        val player = server.getPlayer(playerName) ?: return null
        val profile = player.javaClass.getMethod("getPlayerProfile").invoke(player) ?: return null
        val properties = profile.javaClass
            .getMethod("getProperties", String::class.java)
            .invoke(profile, "textures") as? Collection<*> ?: return null
        properties.firstOrNull()
            ?.let { it.javaClass.getMethod("getValue").invoke(it) as? String }
    }.getOrNull()

    /**
     * ③ 正版 UUID：仅当服务端处于在线模式时，玩家 UUID 才是 Mojang 签发的。
     *
     * 注意：BungeeCord/Velocity 前置 + 子服 `online-mode=false` 是常见架构，
     * 此时这里会返回 null —— 子服无法自行判断正版性，应由代理端承担该级。
     */
    override fun premiumUuid(playerName: String): String? = runCatching {
        if (!server.onlineMode) return null
        server.getPlayer(playerName)?.uniqueId?.toString()
    }.getOrNull()
}
