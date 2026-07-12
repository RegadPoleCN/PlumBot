package me.regadpole.plumbot.platform

interface PlatformPlayerService {
    fun kickPlayer(name: String)

    fun listPlayers(): List<String>

    fun listPlayerString(): String
}
