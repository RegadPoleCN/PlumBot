package me.regadpole.plumbot.adapter.miraimc

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.BotFactory
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformType

object MiraiMCFactory: BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "mirai",
        displayName = "MiraiMC",
        supportedPlatforms = setOf(PlatformType.BUKKIT),
        requiredPlugins = setOf("MiraiMC"),
        capabilities = setOf(
            BotCapability.GROUP_MESSAGE_SEND,
            BotCapability.USER_MESSAGE_SEND,
            BotCapability.IMAGE_SEND,
            BotCapability.GROUP_MEMBER_QUERY,
            BotCapability.GROUP_MEMBER_CHECK,
            BotCapability.GROUP_MESSAGE_RECEIVE,
            BotCapability.GROUP_MEMBER_DECREASE_RECEIVE
        )
    )

    override fun create(context: PlatformContext): IBot {
        return MiraiMCAdapter(context, metadata)
    }
}
