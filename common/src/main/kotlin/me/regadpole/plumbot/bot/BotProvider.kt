package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.platform.PlatformContext

object BotProvider {

    private const val DEFAULT_TYPE = "onebot"

    private val registry = BotRegistry()

    private var bot: IBot? = null

    private var hasLoaded = false

    fun registerFactory(factory: BotFactory) {
        registry.register(factory)
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
            context.logger.log(
                LogLevel.INFO,
                "Loaded bot ${factory.metadata.type} with capabilities: ${factory.metadata.capabilities.joinToString()}"
            )
            hasLoaded = true
        } catch (e: Exception) {
            context.logger.log(LogLevel.ERROR, "Failed to load bot ${factory.metadata.type}: ${e.message ?: e.javaClass.name}")
        }
    }

    private fun resolveFactory(context: PlatformContext, type: String?): BotFactory {
        val requestedType = type?.takeIf { it.isNotBlank() } ?: DEFAULT_TYPE
        val factory = registry.find(requestedType)
        if (factory != null) return factory

        context.logger.log(LogLevel.ERROR, "Unknown bot type: $requestedType! Using OneBot...")
        return registry.find(DEFAULT_TYPE)
            ?: throw IllegalStateException("No bot factory registered for requested type '$requestedType' or fallback '$DEFAULT_TYPE'")
    }

    fun unloadBot() {
        if (bot != null && hasLoaded) {
            bot!!.shutdown()
            bot = null
            hasLoaded = false
        }
    }

    fun getBot(): IBot? {
        return bot
    }
}
