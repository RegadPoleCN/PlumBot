// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.IBot

/** 适配器未声明 [capability] 时抛 [IllegalStateException]。 */
fun IBot.requireCapability(capability: BotCapability) {
    if (capability !in metadata.capabilities) {
        throw IllegalStateException(
            "Adapter '${metadata.displayName}' (${metadata.type}) is missing required capability '$capability'. " +
            "Declared capabilities: ${metadata.capabilities.joinToString()}"
        )
    }
}
