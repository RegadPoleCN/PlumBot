// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
