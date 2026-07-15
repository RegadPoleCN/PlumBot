package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.internal.LogLevel

@StableApi
fun interface PlatformLogger {
    fun log(level: LogLevel, message: String)
}
