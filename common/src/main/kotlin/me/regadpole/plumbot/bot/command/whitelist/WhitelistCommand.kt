// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command.whitelist

interface WhitelistCommand {
    fun execute(message: String, groupId: Long, userId: Long)
}
