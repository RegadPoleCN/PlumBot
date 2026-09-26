package me.regadpole.plumbot.api.database

import kotlinx.datetime.Instant
import me.regadpole.plumbot.api.PublicApi

@PublicApi
data class Binding(
    val id: Int,
    val userId: String,
    val playerName: String,
    val playerUUID: String?,
    val bindingTime: Instant
) {
    companion object
}
