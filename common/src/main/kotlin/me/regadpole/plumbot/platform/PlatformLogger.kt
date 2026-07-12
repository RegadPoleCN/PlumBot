package me.regadpole.plumbot.platform

import me.regadpole.plumbot.internal.LogLevel

fun interface PlatformLogger {
    fun log(level: LogLevel, message: String)
}
