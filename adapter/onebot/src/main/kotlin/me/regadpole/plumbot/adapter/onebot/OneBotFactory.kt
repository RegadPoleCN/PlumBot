package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.BotFactory
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformType
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.net.URI

object OneBotFactory: BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "onebot",
        displayName = "OneBot",
        // Explicitly limited to Bukkit as it is the only platform currently implemented.
        supportedPlatforms = setOf(PlatformType.BUKKIT),
        requiredPlugins = emptySet(),
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
        val address = context.config.getString("bot", "onebot", "address") ?: "localhost:8080"
        val token = context.config.getString("bot", "onebot", "token") ?: ""
        val uri = if (address.contains("://")) URI(address) else URI("ws://$address")
        val client = if (token.isBlank()) WebsocketBotClient(uri) else WebsocketBotClient(uri, token)
        return OneBotAdapter(context, client, metadata)
    }
}
