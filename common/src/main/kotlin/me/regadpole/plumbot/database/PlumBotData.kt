package me.regadpole.plumbot.database

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import taboolib.module.database.ResultProcessorList
import java.sql.ResultSet

data class Binding(
    val id: Int, val userId: String, val playerName: String, val playerUUID: String?, val bindingTime: Instant
) {
    companion object {
        fun of(data: ResultSet?): Binding? {
            data ?: return null
            val id = data.getInt("id")
            val userId = data.getString("user_id") ?: return null
            val playerName = data.getString("player_name") ?: return null
            val playerUUID = data.getString("player_uuid")
            val timeStr = data.getString("binding_time")
            val bindingTime = timeStr?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
            return Binding(id, userId, playerName, playerUUID, bindingTime)
        }

        fun of(data: ResultProcessorList): List<Binding> {
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
    }
}