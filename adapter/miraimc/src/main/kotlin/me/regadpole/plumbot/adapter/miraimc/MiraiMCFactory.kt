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

package me.regadpole.plumbot.adapter.miraimc

import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformType

/**
 * MiraiMC 适配器工厂：声明能力集，并在 `bot.type=miraimc` 时由 `BotProvider` 装配出 [MiraiMCAdapter]。
 *
 * 消息经 MiraiMC 内置的 MiraiBot 会话直连、不走 OneBot，因此宿主必须加载 MiraiMC；
 * `create()` 只做构造，Bot 未登录或不存在要到适配器 `start()` 时才抛 `IllegalStateException`。
 */
@PublicApi
object MiraiMCFactory: BotFactory {
    override val metadata: BotAdapterMetadata = BotAdapterMetadata(
        type = "miraimc",
        displayName = "MiraiMC",
        supportedPlatforms = setOf(PlatformType.BUKKIT, PlatformType.VELOCITY),
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
