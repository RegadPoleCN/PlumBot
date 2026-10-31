// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

/** RCON 一次执行的归一化结果。 */
internal sealed interface RconOutcome {
    data class Success(val lines: List<String>) : RconOutcome
    data class AuthFailed(val detail: String) : RconOutcome
    data class Error(val detail: String) : RconOutcome
}
