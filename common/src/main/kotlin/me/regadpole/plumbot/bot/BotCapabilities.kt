package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.bot.IBot

/**
 * Throws [IllegalStateException] if the adapter that created this bot
 * does not declare the requested [capability].
 */
fun IBot.requireCapability(capability: BotCapability) {
    if (capability !in metadata.capabilities) {
        throw IllegalStateException(
            "Adapter '${metadata.displayName}' (${metadata.type}) is missing required capability '$capability'. " +
            "Declared capabilities: ${metadata.capabilities.joinToString()}"
        )
    }
}
