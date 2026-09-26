package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.platform.PlatformContext

@StableApi
interface BotFactory {
    val metadata: BotAdapterMetadata

    fun create(context: PlatformContext): IBot
}
