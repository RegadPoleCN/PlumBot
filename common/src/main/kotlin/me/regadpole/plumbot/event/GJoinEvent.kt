package me.regadpole.plumbot.event

import me.regadpole.plumbot.util.GPlayer

abstract class GJoinEvent(val player: GPlayer) {
    abstract fun kick(message: String)
}