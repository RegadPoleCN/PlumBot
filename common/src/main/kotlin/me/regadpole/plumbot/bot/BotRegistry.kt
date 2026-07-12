package me.regadpole.plumbot.bot

import me.regadpole.plumbot.platform.PlatformType

class BotRegistry {
    private val factories = linkedMapOf<String, BotFactory>()

    fun register(factory: BotFactory) {
        factories[normalize(factory.metadata.type)] = factory
    }

    fun unregister(type: String) {
        factories.remove(normalize(type))
    }

    fun clear() {
        factories.clear()
    }

    fun find(type: String?): BotFactory? {
        if (type.isNullOrBlank()) return factories.values.firstOrNull()
        return factories[normalize(type)]
    }

    fun compatibleFactories(platformType: PlatformType): List<BotFactory> {
        return factories.values.filter { it.metadata.supports(platformType) }
    }

    fun registeredTypes(): Set<String> {
        return factories.keys.toSet()
    }

    fun factories(): List<BotFactory> {
        return factories.values.toList()
    }

    private fun normalize(type: String): String {
        return type.lowercase()
    }
}
