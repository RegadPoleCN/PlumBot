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

package me.regadpole.plumbot.hytale.listener

import com.hypixel.hytale.server.core.plugin.PluginBase
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.hytale.platform.HytalePlugin
import com.hypixel.hytale.server.core.plugin.JavaPlugin

/**
 * Hytale 第三方扩展生态守护管理器。
 * 当外部插件注销或热重载时安全回收其持有的事件句柄与 Bot 适配器，防止内存泄漏。
 */
class HytalePluginListener {

    fun onPluginUnload(pluginBase: PluginBase) {
        if (pluginBase !is JavaPlugin) return
        val plugin = HytalePlugin(pluginBase)
        val api = runCatching { PlumBotAPI.get() }.getOrNull()
        api?.extensionRegistry?.unregisterAllFor(plugin)
        BotEventDispatcher.unregisterAllFor(plugin)
    }
}
