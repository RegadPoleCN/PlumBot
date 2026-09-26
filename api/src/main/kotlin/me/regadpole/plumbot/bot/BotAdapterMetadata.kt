package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.platform.PlatformType

@PublicApi
data class BotAdapterMetadata(
    val type: String,
    val displayName: String = type,
    val supportedPlatforms: Set<PlatformType> = emptySet(),
    val requiredPlugins: Set<String> = emptySet(),
    val capabilities: Set<BotCapability> = emptySet()
) {
    fun supports(platformType: PlatformType): Boolean {
        return supportedPlatforms.isEmpty() || supportedPlatforms.contains(platformType)
    }
}
