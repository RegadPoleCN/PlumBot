package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PublicApi

@PublicApi
interface BotExtensionRegistry {
    fun registerExternalFactory(factory: BotFactory, plugin: Plugin): Boolean
    fun unregisterExternalFactory(type: String, plugin: Plugin): Boolean
    fun unregisterAllFor(plugin: Plugin): Int
    fun snapshot(): List<Pair<String, Plugin>>
}
