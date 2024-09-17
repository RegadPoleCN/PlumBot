package me.regadpole.plumbot.util

abstract class GPlayer(val name: String, var position: GPosition, val server: String, val isAdmin: Boolean) {
    abstract fun teleport()
    abstract fun sendMessage(message: String)
}