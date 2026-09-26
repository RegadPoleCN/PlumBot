package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}
