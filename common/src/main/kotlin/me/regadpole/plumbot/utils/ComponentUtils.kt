package me.regadpole.plumbot.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

fun getComponentFromMiniMsg(msg: String): Component {
    return MiniMessage.miniMessage().deserialize(msg)
}
fun getLegacyFromComponent(component: Component): String {
    return LegacyComponentSerializer.legacyAmpersand().serialize(component)
}