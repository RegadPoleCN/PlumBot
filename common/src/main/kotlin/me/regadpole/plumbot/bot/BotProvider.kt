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

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext

@StableApi
object BotProvider {

    private const val DEFAULT_TYPE = "onebot"

    private val registry = BotRegistry()

    private var bot: IBot? = null

    private var hasLoaded = false

    /**
     * @deprecated 零调用成员：请改用 `getBot() != null` 判断（D8 弃用周期，主版本内保留）。
     */
    @Deprecated("改用 getBot() != null")
    val isReady: Boolean
        get() = hasLoaded && bot != null

    fun registerFactory(factory: BotFactory) {
        registry.register(factory)
    }

    /**
     * Unregister a single bot factory by its [BotAdapterMetadata.type]. Returns
     * `true` if a factory was removed, `false` otherwise. Used by third-party
     * extension cleanup paths that need to remove specific adapters without
     * nuking the entire registry.
     */
    fun unregisterFactory(type: String): Boolean {
        val key = type.lowercase()
        val before = registry.factories().map { it.metadata.type.lowercase() }
        if (key !in before) return false
        registry.unregister(type)
        return true
    }

    fun clearFactories() {
        registry.clear()
    }

    fun availableAdapters(): List<BotAdapterMetadata> {
        return registry.factories().map { it.metadata }
    }

    fun loadBot(plugin: PlumBot, type: String?) {
        loadBot(plugin.platform, type)
    }

    fun loadBot(context: PlatformContext, type: String?) {
        val factory = resolveFactory(context, type)

        if (!factory.metadata.supports(context.platformType)) {
            throw IllegalStateException("Bot type ${factory.metadata.type} is not compatible with platform ${context.platformType}")
        }

        val missingPlugins = factory.metadata.requiredPlugins.filterNot { context.isPluginAvailable(it) }
        if (missingPlugins.isNotEmpty()) {
            throw IllegalStateException("Bot type ${factory.metadata.type} requires missing plugins: ${missingPlugins.joinToString()}")
        }

        try {
            bot = factory.create(context).start()
            context.log(
                LogLevel.INFO,
                "Loaded bot ${factory.metadata.type} with capabilities: ${factory.metadata.capabilities.joinToString()}"
            )
            hasLoaded = true
            HealthCounters.increment(HealthCounters.Key.BOT_STARTED)
        } catch (e: Exception) {
            context.log(LogLevel.ERROR, "Failed to load bot ${factory.metadata.type}: ${e.message ?: e.javaClass.name}")
            context.log(LogLevel.ERROR, e.stackTraceToString())
            hasLoaded = false
            // 半初始化实例可能已持有连接/协程作用域：置空并尽力释放，避免残留（否则 unloadBot 也够不着它）
            val failed = bot
            bot = null
            runCatching { failed?.shutdown() }
            throw IllegalStateException("Failed to load bot ${factory.metadata.type}", e)
        }
    }

    private fun resolveFactory(context: PlatformContext, type: String?): BotFactory {
        val requestedType = type?.takeIf { it.isNotBlank() } ?: DEFAULT_TYPE
        val factory = registry.find(requestedType)
        if (factory != null) return factory

        context.log(LogLevel.ERROR, "Unknown bot type: $requestedType! Using OneBot...")
        return registry.find(DEFAULT_TYPE)
            ?: throw IllegalStateException("No bot factory registered for requested type '$requestedType' or fallback '$DEFAULT_TYPE'")
    }

    /**
     * 卸载当前 Bot。
     *
     * **无条件尝试释放**：此前的实现要求 `bot != null && hasLoaded` 才调用 `shutdown()`，
     * 而"加载过程中失败"会留下 `bot` 已赋值、`hasLoaded=false` 的半初始化实例
     * （连接与协程作用域都还在），于是它永远不会被回收。
     */
    fun unloadBot() {
        val current = bot
        bot = null
        hasLoaded = false
        HealthCounters.increment(HealthCounters.Key.BOT_UNLOADED)
        runCatching { current?.shutdown() }
            .onFailure { System.err.println("[PlumBot] Bot 卸载失败：${it.message ?: it.javaClass.simpleName}") }

        // Public-API listener cleanup. Public-API handles returned by
        // PlumBotAPI.subscribeXxx are best-effort closed via this hook.
        BotEventDispatcher.clearAllListeners()
    }

    fun getBot(): IBot? {
        return bot
    }
}
