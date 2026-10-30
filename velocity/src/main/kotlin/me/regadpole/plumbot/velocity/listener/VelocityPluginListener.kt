// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.velocity.listener

import com.velocitypowered.api.plugin.PluginContainer
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.velocity.platform.VelocityPlugin
import org.slf4j.Logger

/**
 * Velocity 第三方扩展生态守护管理器。
 *
 * 当外部插件注销或热重载时：
 * 1. 自动清除该插件订阅的所有群事件监听器 ([PlumBotAPI.subscribeGroupMessage])；
 * 2. 自动卸载该插件动态注册的外部 [me.regadpole.plumbot.api.bot.BotFactory]；
 * 从根源杜绝 Metaspace 内存泄漏。
 */
class VelocityPluginListener(
    private val logger: Logger
) {

    fun onPluginUnload(container: PluginContainer) {
        val plugin = VelocityPlugin(container)
        val api = runCatching { PlumBotAPI.get() }.getOrNull()
        if (api != null) {
            val removedFactories = api.extensionRegistry.unregisterAllFor(plugin)
            if (removedFactories > 0) {
                logger.info("[PlumBot] 外部插件 ${plugin.name} 卸载，已清理其注册的 $removedFactories 个 BotFactory")
            }
        }
        BotEventDispatcher.unregisterAllFor(plugin)
    }
}
