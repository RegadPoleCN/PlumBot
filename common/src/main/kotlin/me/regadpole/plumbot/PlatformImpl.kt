package me.regadpole.plumbot

abstract class PlatformImpl {
    abstract fun load()
    abstract fun getPlayerList() : List<String>
    abstract fun getTPS(): List<Double>
    abstract fun dispatchCommand(cmd: String): String
}