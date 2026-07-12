package me.regadpole.plumbot.platform

import net.kyori.adventure.text.Component

fun interface PlatformMessenger {
    fun sendMessage(message: Component)
}
