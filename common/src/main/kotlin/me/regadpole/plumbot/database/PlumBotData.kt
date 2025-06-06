package me.regadpole.plumbot.database

import kotlinx.datetime.Instant
import taboolib.module.database.ResultProcessorList
import java.sql.ResultSet

data class Binding(
    val id: Int, val userId: String, val playerName: String, val playerUUID: String?, val bindingTime: Instant
) {
    companion object {
        fun of(data: ResultSet?): Binding? {
            data ?: return null
            data.let {
                it.getInt("id").also { id ->
                    it.getString("user_id").also { userId ->
                        it.getString("player_name").also { playerName ->
                            it.getString("player_uuid").also { playerUUID ->
                                Instant.fromEpochMilliseconds(it.getString("binding_time").toLong()).also { bindingTime ->
                                    return Binding(id, userId, playerName, playerUUID, bindingTime)
                                }
                            }
                        }
                    }
                }

            }
        }

        fun of(data: ResultProcessorList): List<Binding> {
            val result = ArrayList<Binding>()
            data.forEach {
                if (wasNull()) return@forEach
                getInt("id").also { id ->
                    getString("user_id").also { userId ->
                        getString("player_name").also { playerName ->
                            getString("player_uuid").also { playerUUID ->
                                Instant.fromEpochMilliseconds(getString("binding_time").toLong()).also { bindingTime ->
                                    result.add(Binding(id, userId, playerName, playerUUID, bindingTime))
                                }
                            }
                        }
                    }
                }

            }
            return result
        }
    }
}