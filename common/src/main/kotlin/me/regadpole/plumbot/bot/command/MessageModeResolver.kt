// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

object MessageModeResolver {

    fun resolve(message: String, mode: Int, prefix: String?): String? {
        return when (mode) {
            0 -> message
            1 -> {
                val p = prefix ?: ""
                if (p.isNotEmpty() && message.startsWith(p)) {
                    message.removePrefix(p)
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
