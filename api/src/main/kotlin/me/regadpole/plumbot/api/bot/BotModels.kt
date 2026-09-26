package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformType

@PublicApi
enum class BotCapability {
    GROUP_MESSAGE_SEND,
    USER_MESSAGE_SEND,
    IMAGE_SEND,
    GROUP_MEMBER_QUERY,
    GROUP_MEMBER_CHECK,
    GROUP_MESSAGE_RECEIVE,
    GROUP_MEMBER_DECREASE_RECEIVE
}

@PublicApi
data class BotAdapterMetadata(
    val type: String,
    val displayName: String = type,
    val supportedPlatforms: Set<PlatformType> = emptySet(),
    val requiredPlugins: Set<String> = emptySet(),
    val capabilities: Set<BotCapability> = emptySet()
) {
    fun supports(platformType: PlatformType): Boolean =
        supportedPlatforms.isEmpty() || supportedPlatforms.contains(platformType)
}

@PublicApi
data class MemberInfo(
    val userId: Long,
    val name: String,
    val card: String,
    val role: String = "member"
)
