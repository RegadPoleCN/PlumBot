package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.StableApi
import net.kyori.adventure.text.Component

@StableApi
fun interface PlatformMessenger {
    fun sendMessage(message: Component)
}
