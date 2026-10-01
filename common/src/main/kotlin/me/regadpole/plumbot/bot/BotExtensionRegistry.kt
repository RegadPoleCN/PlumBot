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
 * Runtime registry for **third-party** bot adapters.
 *
 * Unlike the compile-time registration used by the built-in adapters
 * ([OneBotFactory], [MiraiMCFactory]), this registry accepts
 * [me.regadpole.plumbot.bot.BotFactory] instances supplied at runtime and
 * records the originating [Plugin] so they can be deregistered when the
 * plugin is disabled.
 *
 * The registry wraps [BotProvider]'s underlying [BotRegistry] so that all
 * factories (built-in or external) share the same `type` namespace. A duplicate
 * type registration is rejected with `false` and a WARN log entry — the
 * built-in adapter always wins so the host server stays in a known state.
 *
 * Obtain via [PlumBotAPI.getExtensionRegistry].
 */
@PublicApi
class DefaultBotExtensionRegistry internal constructor(
    private val provider: BotProvider,
) : BotExtensionRegistry {
    /** Tracks (type, plugin) → factory for batch cleanup on plugin disable. */
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
            // Built-in factories do not appear in `registrations`; check provider directly.
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
     * Unregister a single external factory by type and originating plugin.
     *
     * @return `true` if a registration was removed, `false` otherwise.
     */
    @PublicApi
    override fun unregisterExternalFactory(type: String, plugin: Plugin): Boolean {
        val key = type.lowercase() to plugin
        synchronized(lock) {
            val factory = registrations.remove(key) ?: return false
            // Only remove from the provider if no other plugin owns this type — i.e. the
            // entry was truly external. If a built-in of the same type is also registered
            // we keep the provider's view intact.
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
     * Unregister every factory that was registered by [plugin]. Invoked by
     * Bukkit's `PluginDisableEvent` to keep the registry clean when a third-party
     * plugin unloads.
     *
     * Note: this **does not** unload any currently-running bot owned by the
     * factory, leaving shutdown decisions to the main host plugin.
     *
     * @return number of registrations removed.
     */
    @PublicApi
    override fun unregisterAllFor(plugin: Plugin): Int {
        synchronized(lock) {
            val keys = registrations.keys.filter { it.second == plugin }
            if (keys.isEmpty()) return 0
            keys.forEach { registrations.remove(it) }
            // Group remaining types: only call unregisterFactory for types where this
            // plugin was the sole owner, so built-ins and other plugins' factories
            // survive.
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

    /**
     * Snapshot of registered (type, plugin) pairs. Useful for diagnostics / tests.
     */
    @PublicApi
    override fun snapshot(): List<Pair<String, Plugin>> = synchronized(lock) {
        registrations.keys.toList()
    }

    private fun warn(message: String) {
        // Use the attached plugin logger if available; fall back to stderr.
        val plugin = BotEventDispatcher.attachedPlugin
        if (plugin != null) {
            plugin.log(me.regadpole.plumbot.internal.LogLevel.WARN, message)
        } else {
            System.err.println(message)
        }
    }

}
