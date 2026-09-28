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

package me.regadpole.plumbot.velocity.listener

import com.velocitypowered.api.plugin.PluginContainer
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.bot.BotEventDispatcher
import me.regadpole.plumbot.bot.BotProvider
import org.slf4j.Logger

/**
 * 包装 Velocity 原生 [PluginContainer] 为 PlumBot 内部通用的 [Plugin] 凭据。
 */
class VelocityPluginWrapper(
    private val container: PluginContainer
) : Plugin {
    override val name: String = container.description.id
    override val version: String = container.description.version.orElse("unknown")
    override val isEnabled: Boolean = true

    override fun equals(other: Any?): Boolean =
        other is Plugin && other.name.equals(name, ignoreCase = true)

    override fun hashCode(): Int = name.lowercase().hashCode()
}

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
        val plugin = VelocityPluginWrapper(container)
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
