// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.api.platform.PlatformAvatarLayer
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.hytale.avatar.HytaleAvatarResolver

class HytalePlayerService : PlatformPlayerService {

    override fun kickPlayer(name: String) {
        val player = Universe.get().getPlayerByUsername(name, NameMatching.EXACT) ?: return
        player.packetHandler.disconnect(Message.raw("您已被移出服务器。"))
    }

    override fun listPlayers(): List<String> =
        Universe.get().players.map { it.username }

    override fun listPlayerString(): String =
        listPlayers().joinToString(", ")

    override fun getPlayerUuid(name: String): String? =
        Universe.get().getPlayerByUsername(name, NameMatching.EXACT)?.uuid?.toString()

    /**
     * 组装 Hytale 原生外观图层（见 [HytaleAvatarResolver]）。
     *
     * 资产**不随插件分发**：默认从服务端自身的 asset pack 读取，服主可在此之前
     * 用 `plugins/PlumBot/assets/characters/` 覆盖。
     */
    override fun resolveAvatarLayers(playerName: String): List<PlatformAvatarLayer> =
        HytaleAvatarResolver.resolveLayers(playerName)
}
