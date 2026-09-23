package me.regadpole.plumbot.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

fun getComponentFromMiniMsg(msg: String): Component {
    return MiniMessage.miniMessage().deserialize(msg)
}

fun escapeMiniMessageTags(text: String): String {
    return MiniMessage.miniMessage().escapeTags(text)
}

fun getLegacyFromComponent(component: Component): String {
    return LegacyComponentSerializer.legacyAmpersand().serialize(component)
}

fun getPlainTextFromComponent(component: Component): String {
    val builder = StringBuilder()
    fun extract(c: Component) {
        if (c is TextComponent) {
            builder.append(c.content())
        }
        for (child in c.children()) {
            extract(child)
        }
    }
    extract(component)
    return builder.toString()
}

fun stripMinecraftFormatting(text: String): String {
    val component = LegacyComponentSerializer.legacyAmpersand().deserialize(
        LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(text)
        )
    )
    return getPlainTextFromComponent(component)
}