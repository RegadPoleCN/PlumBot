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

package me.regadpole.plumbot.bukkit.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.plugin.Plugin

/**
 * Bukkit 侧的消息落地。
 *
 * 不再依赖 `adventure-platform-bukkit`（它会把 kyori 类带进产物，与宿主版本/类身份冲突）；
 * 这里只把 [Component] 序列化为传统 `§` 文本，再交给 Bukkit 自己的 `sendMessage(String)`。
 * 这样无论宿主是否自带 Adventure（Paper 有 / Spigot 由 Libby 回退装载），行为都一致。
 */
class BukkitPlatformMessenger(
    private val plugin: Plugin,
) {
    private val legacy: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

    fun sendMessage(message: Component) {
        val text = legacy.serialize(message)
        plugin.server.consoleSender.sendMessage(text)
        plugin.server.onlinePlayers.forEach { it.sendMessage(text) }
    }
}
