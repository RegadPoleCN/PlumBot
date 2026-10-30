// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.platform.PlatformContext

@StableApi
interface BotFactory {
    val metadata: BotAdapterMetadata

    fun create(context: PlatformContext): IBot
}
