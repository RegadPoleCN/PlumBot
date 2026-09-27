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
    val timeStr = data.getString("binding_time")
    val bindingTime = timeStr?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
    return Binding(id, userId, playerName, playerUUID, bindingTime)
}

fun Binding.Companion.of(data: ResultProcessorList): List<Binding> {
    val result = ArrayList<Binding>()
    data.forEach {
        if (wasNull()) return@forEach
        val id = getInt("id")
        val userId = getString("user_id") ?: return@forEach
        val playerName = getString("player_name") ?: return@forEach
        val playerUUID = getString("player_uuid")
        val timeStr = getString("binding_time")
        val bindingTime = timeStr?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
        result.add(Binding(id, userId, playerName, playerUUID, bindingTime))
    }
    return result
}