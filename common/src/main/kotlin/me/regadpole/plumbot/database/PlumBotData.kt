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

package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.Binding

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import taboolib.module.database.ResultProcessorList
import java.sql.ResultSet

fun Binding.Companion.of(data: ResultSet?): Binding? {
    data ?: return null
    val id = data.getInt("id")
    val userId = data.getString("user_id") ?: return null
    val playerName = data.getString("player_name") ?: return null
    val playerUUID = data.getString("player_uuid")
    val sourceGroup = data.getString("source_group")
    val timeStr = data.getString("binding_time")
    val bindingTime = timeStr?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
    return Binding(id, userId, playerName, playerUUID, bindingTime, sourceGroup)
}

fun Binding.Companion.of(data: ResultProcessorList): List<Binding> {
    val result = ArrayList<Binding>()
    data.forEach {
        // 不要在这里调用 `wasNull()`：JDBC 规范里它指的是"上一次 getXxx 读到的列"，
        // 而本行尚未读任何列。SQLite 驱动会直接抛 `column -1 out of bounds`（MySQL 恰好返回 false，
        // 因此该缺陷只在 SQLite 后端暴露）。空值由下面的 null 判断处理。
        val id = getInt("id")
        val userId = getString("user_id") ?: return@forEach
        val playerName = getString("player_name") ?: return@forEach
        val playerUUID = getString("player_uuid")
        val sourceGroup = getString("source_group")
        val timeStr = getString("binding_time")
        val bindingTime = timeStr?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
        result.add(Binding(id, userId, playerName, playerUUID, bindingTime, sourceGroup))
    }
    return result
}