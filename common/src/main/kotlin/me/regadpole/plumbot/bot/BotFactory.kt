package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.platform.PlatformContext

@StableApi
interface BotFactory {
    val metadata: BotAdapterMetadata

    fun create(context: PlatformContext): IBot
}
