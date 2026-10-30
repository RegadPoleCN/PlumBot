// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformType
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.net.URI

/**
 * OneBot 适配器工厂：读 `bot.onebot.address` / `token` 构造 AOneBot 的 [WebsocketBotClient]，
 * 并装配 [OneBotAdapter]；地址未带 scheme 时按 `ws://` 补全，token 为空即无鉴权连接。
 *
 * `create()` 不发起连接：连接在适配器 `start()` 之后由 IO 协程异步建立，初次失败只记 WARN，
 * 之后指数退避重连，因此反向 WS 端晚启动不会让插件加载失败。
 */
@PublicApi
object OneBotFactory: BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "onebot",
        displayName = "OneBot",
        supportedPlatforms = setOf(PlatformType.BUKKIT, PlatformType.VELOCITY, PlatformType.HYTALE),
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
