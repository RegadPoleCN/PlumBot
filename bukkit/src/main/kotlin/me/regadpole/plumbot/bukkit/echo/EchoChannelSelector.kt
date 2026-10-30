// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import java.util.Locale

/** 通道候选顺序解析；纯函数，便于测试 forced/默认/去重。 */
internal object EchoChannelSelector {

    fun candidates(settings: EchoSettings): List<String> {
        val requested = if (settings.forced.isNotEmpty()) settings.forced else settings.channels
        return requested
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun isKnown(name: String): Boolean = name in EchoChannels.KNOWN
}
