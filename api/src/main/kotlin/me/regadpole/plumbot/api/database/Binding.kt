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

package me.regadpole.plumbot.api.database

import kotlinx.datetime.Instant
import me.regadpole.plumbot.api.PublicApi

@PublicApi
data class Binding(
    val id: Int,
    val userId: String,
    val playerName: String,
    val playerUUID: String?,
    val bindingTime: Instant,
    /**
     * 该绑定是在哪个 QQ 群写入的（群号字符串）；由游戏内指令 `/plumbot bind add`
     * 或外部 API（`PlumBotAPI.addBinding`）写入的记录为 `null`。
     *
     * 放在末尾并带默认值：外部实现按位置构造本类时仍然源码兼容。
     */
    val sourceGroup: String? = null
) {
    companion object
}
