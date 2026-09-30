/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
