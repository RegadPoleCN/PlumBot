package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi

@StableApi
interface PlatformPlayerService {
    fun kickPlayer(name: String)

    fun listPlayers(): List<String>

    fun listPlayerString(): String
}
