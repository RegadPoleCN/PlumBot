package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.StableApi

@StableApi
fun interface PlatformLogger {
    fun log(level: LogLevel, message: String)
}
