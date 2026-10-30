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

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.bot.BotExtensionRegistry
import me.regadpole.plumbot.api.bot.BotFactory

/**
 * 第三方 bot 适配器的运行期注册表。
 *
 * 内置适配器由宿主在启动时通过 [BotProvider.registerFactory] 登记；本注册表接受运行期传入的
 * [BotFactory] 并记录来源 [Plugin]，插件被禁用时可整批注销。
 *
 * 它与 [BotProvider] 底层的 [BotRegistry] 共用同一个 `type` 命名空间：重复的 type 会被拒绝并抛
 * [me.regadpole.plumbot.api.exception.AdapterRegistrationException]；内置适配器优先，宿主因此
 * 始终处于已知状态。
 *
 * 通过 [me.regadpole.plumbot.api.PlumBotAPI.extensionRegistry] 获取。
 */
@PublicApi
class DefaultBotExtensionRegistry internal constructor(
    private val provider: BotProvider,
) : BotExtensionRegistry {
    /** (type, plugin) → factory，供插件禁用时整批注销。 */
    private val registrations: MutableMap<Pair<String, Plugin>, BotFactory> = linkedMapOf()
    private val lock = Any()

    @PublicApi
    override fun registerExternalFactory(factory: BotFactory, plugin: Plugin): Boolean {
        val type = factory.metadata.type
        synchronized(lock) {
            val key = type.lowercase() to plugin
            if (registrations.containsKey(key)) {
                throw me.regadpole.plumbot.api.exception.AdapterRegistrationException(
                    "Factory for type '$type' already registered by ${plugin.name}"
                )
            }
            val existingType = registrations.keys.firstOrNull { it.first == type.lowercase() }
            if (existingType != null) {
                throw me.regadpole.plumbot.api.exception.AdapterRegistrationException(
                    "Type '$type' already registered by ${existingType.second.name}"
                )
            }
            // 内置 factory 不在此表中，需直接问 provider。
            val builtIn = runCatching { provider.availableAdapters() }
                .getOrDefault(emptyList())
                .any { it.type.equals(type, ignoreCase = true) }
            if (builtIn) {
                throw me.regadpole.plumbot.api.exception.AdapterRegistrationException(
                    "Type '$type' is a built-in adapter; registration rejected"
                )
            }
            runCatching { provider.registerFactory(factory) }
                .onFailure {
                    throw me.regadpole.plumbot.api.exception.AdapterRegistrationException(
                        "provider.registerFactory failed: ${it.message}"
                    )
                }
            registrations[key] = factory
            return true
        }
    }

    /**
     * 按 type 与来源 [Plugin] 注销单个外部 factory；没有对应登记时返回 `false`。
     */
    @PublicApi
    override fun unregisterExternalFactory(type: String, plugin: Plugin): Boolean {
        val key = type.lowercase() to plugin
        synchronized(lock) {
            val factory = registrations.remove(key) ?: return false
            // 仅当没有其他插件仍持有该 type 时才从 provider 注销，否则会连带删掉别人的 factory。
            val stillOwned = registrations.any { it.key.first == type.lowercase() }
            val wasBuiltin = runCatching { provider.availableAdapters() }
                .getOrDefault(emptyList())
                .any { it.type.equals(factory.metadata.type, ignoreCase = true) && !stillOwned }
            if (!stillOwned) {
                runCatching { provider.unregisterFactory(type) }
                    .onFailure { warn("BotExtensionRegistry: provider.unregisterFactory('$type') failed: ${it.message}") }
            }
            debug("BotExtensionRegistry: unregistered type '${factory.metadata.type}' from ${plugin.name} (wasBuiltin=$wasBuiltin, stillOwned=$stillOwned)")
            return true
        }
    }

    /**
     * 注销 [plugin] 登记过的全部 factory，由宿主在插件禁用（Bukkit 的 `PluginDisableEvent`）时调用。
     *
     * **不会**停掉 factory 正在托管的 bot —— 停机决策留给宿主插件。
     *
     * @return 被注销的登记数量
     */
    @PublicApi
    override fun unregisterAllFor(plugin: Plugin): Int {
        synchronized(lock) {
            val keys = registrations.keys.filter { it.second == plugin }
            if (keys.isEmpty()) return 0
            keys.forEach { registrations.remove(it) }
            // 只注销本插件独占的 type，内置与其他插件的 factory 必须保留。
            val remainingTypes = registrations.keys.map { it.first }.toSet()
            val myTypes = keys.map { it.first }
            myTypes.forEach { type ->
                if (type !in remainingTypes) {
                    runCatching { provider.unregisterFactory(type) }
                        .onFailure { warn("BotExtensionRegistry: provider.unregisterFactory('$type') failed: ${it.message}") }
                }
            }
            debug("BotExtensionRegistry: unregistered ${keys.size} factories from ${plugin.name}")
            return keys.size
        }
    }

    private fun debug(message: String) {
        val plugin = BotEventDispatcher.attachedPlugin
        plugin?.log(me.regadpole.plumbot.internal.LogLevel.DEBUG, message)
    }

    /** 已登记 (type, plugin) 的快照，供诊断与测试使用。 */
    @PublicApi
    override fun snapshot(): List<Pair<String, Plugin>> = synchronized(lock) {
        registrations.keys.toList()
    }

    private fun warn(message: String) {
        // 有主插件时走它的 logger，否则退回 stderr。
        val plugin = BotEventDispatcher.attachedPlugin
        if (plugin != null) {
            plugin.log(me.regadpole.plumbot.internal.LogLevel.WARN, message)
        } else {
            System.err.println(message)
        }
    }

}
