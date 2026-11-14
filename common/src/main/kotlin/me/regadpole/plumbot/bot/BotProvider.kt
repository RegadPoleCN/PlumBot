// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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

    /** @deprecated 零调用成员：请改用 `getBot() != null` 判断（主版本内保留）。 */
    @Deprecated("改用 getBot() != null")
    val isReady: Boolean
        get() = hasLoaded && bot != null

    fun registerFactory(factory: BotFactory) {
        registry.register(factory)
    }

    /**
     * 按 [BotAdapterMetadata.type] 注销单个 factory：供第三方扩展精确清理，不必清空整个注册表。
     *
     * @return 确实删除了 factory 时返回 `true`，否则 `false`
     */
    fun unregisterFactory(type: String): Boolean {
        return registry.unregister(type)
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

        var fresh: IBot? = null
        try {
            // 新实例先落局部变量：start() 抛异常时 `bot` 字段仍是旧值，
            // 直接用它清理会误关旧实例，真正泄漏的半初始化新实例反而无人释放。
            fresh = factory.create(context)
            bot = fresh.start()
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
            // 只释放本次新建的实例（可能已持有连接/协程作用域），避免残留（否则 unloadBot 也够不着它）
            bot = null
            runCatching { fresh?.shutdown() }
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
     * **无条件尝试释放**：加载失败会留下 `bot` 已赋值、`hasLoaded=false` 的半初始化实例
     * （连接与协程作用域都还在），若以 `hasLoaded` 为条件调用 `shutdown()`，它就永远不会被回收。
     */
    fun unloadBot() {
        val current = bot
        bot = null
        hasLoaded = false
        HealthCounters.increment(HealthCounters.Key.BOT_UNLOADED)
        runCatching { current?.shutdown() }
            .onFailure { System.err.println("[PlumBot] Bot 卸载失败：${it.message ?: it.javaClass.simpleName}") }

        // 顺带清理 Public-API 监听器：PlumBotAPI.subscribeXxx 返回的 handle 在此尽力关闭。
        BotEventDispatcher.clearAllListeners()
    }

    fun getBot(): IBot? {
        return bot
    }
}
