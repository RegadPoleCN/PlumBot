// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.database

import kotlinx.datetime.Instant
import me.regadpole.plumbot.api.PublicApi

@PublicApi
data class Binding(
    val id: Int,
    val userId: String,
    val playerName: String,
    val playerUUID: String?,
    /**
     * 绑定时间；为 null 表示数据库里的 binding_time 字段存在但已损坏（非合法 epoch 毫秒），
     * 读取层不会伪造时间，上层应显示"未知"。历史数据行（无该字段值）由读取层回落当前时间。
     */
    val bindingTime: Instant?,
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
