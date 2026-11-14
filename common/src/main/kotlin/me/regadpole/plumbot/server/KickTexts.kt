// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.server

import me.regadpole.plumbot.config.Messages

/**
 * 被拒玩家看到的加群引导文案，三平台共用。
 *
 * `%groups%` 传的是互通群列表：用「、」连接成人读格式，
 * 而不是 `List.toString()` 的 `[123, 456]` 括号逗号形。
 */
object KickTexts {

    fun kickServer(groups: List<Long>): String =
        Messages.kickServer.replace("%groups%", groups.joinToString("、"))
}
