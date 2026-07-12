package me.regadpole.plumbot.server

data class PreLoginResult(
    val allowed: Boolean,
    val kickMessage: String? = null
)
