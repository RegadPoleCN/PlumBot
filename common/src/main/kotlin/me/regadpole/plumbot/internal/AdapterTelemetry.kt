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

package me.regadpole.plumbot.internal

import me.regadpole.plumbot.bot.BotProvider

/**
 * bStats「适配器类别」上报（chart 维度即适配器类别）。
 *
 * bStats 只能说明装了多少台服务器，说明不了**用户在用哪种接入方式**（OneBot 正向 vs MiraiMC
 * 桥接），而后者直接决定适配器层的维护优先级。
 *
 * 只上报**类别名**（`onebot` / `miraimc` / `unknown`），不含群号、账号、服务器名等任何标识信息。
 */
object AdapterTelemetry {

    const val CHART_ID = "adapter_type"

    /**
     * 当前适配器类别；未加载时返回 `unknown`。
     *
     * 只从实际加载的适配器取：配置里的 `bot.type` 是**推测性**回退 —— bStats 的 chart 在运行期
     * 周期求值，那时适配器早已加载，没有证据表明两者会不一致。单一来源更省维护。
     */
    fun current(): String =
        BotProvider.getBot()?.metadata?.type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: UNKNOWN

    const val UNKNOWN = "unknown"
}