package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
enum class PlatformType {
    BUKKIT,
    VELOCITY,
    BUNGEE,
    STANDALONE
}

@PublicApi
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}

@PublicApi
enum class PlatformCapability {
    CHAT_RECEIVE,
    CHAT_BROADCAST,
    PRE_LOGIN_INTERCEPT,
    PLAYER_JOIN_BROADCAST,
    PLAYER_QUIT_BROADCAST,
    SERVER_SWITCH_BROADCAST,
    PLAYER_DEATH_BROADCAST,
    PLAYER_ADVANCEMENT_BROADCAST,
    SERVER_TPS_METRICS,
    COMMAND_DISPATCH
}
