package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
enum class PlatformType {
    BUKKIT,
    SPONGE,
    VELOCITY,
    BUNGEECORD,
    STANDALONE,
    UNKNOWN
}
