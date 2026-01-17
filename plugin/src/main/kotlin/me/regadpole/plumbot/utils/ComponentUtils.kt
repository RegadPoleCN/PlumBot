package me.regadpole.plumbot.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

fun getComponentFromMiniMsg(msg: String): Component {
    return MiniMessage.miniMessage().deserialize(msg)
}