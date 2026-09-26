package me.regadpole.plumbot.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** 将 MiniMessage 字符串反序列化为 Component */
fun String.asMiniMessage(): Component = MiniMessage.miniMessage().deserialize(this)

/** 转义 MiniMessage 标签 */
fun String.escapeMiniMessage(): String = MiniMessage.miniMessage().escapeTags(this)

/** Component 转 & 颜色字符串 */
fun Component.toLegacyAmpersand(): String = LegacyComponentSerializer.legacyAmpersand().serialize(this)

/** 递归提取 Component 中的所有纯文本，完全剥除颜色和样式 */
fun Component.toPlainText(): String {
    val builder = StringBuilder()
    fun extract(c: Component) {
        if (c is TextComponent) {
            builder.append(c.content())
        }
        for (child in c.children()) {
            extract(child)
        }
    }
    extract(this)
    return builder.toString()
}

/** 剥除传统 Minecraft 格式码 */
fun String.stripMinecraftFormatting(): String {
    val component = LegacyComponentSerializer.legacyAmpersand().deserialize(
        LegacyComponentSerializer.legacySection().serialize(
            LegacyComponentSerializer.legacyAmpersand().deserialize(this)
        )
    )
    return component.toPlainText()
}

// 保持既有顶层函数无缝兼容
fun getComponentFromMiniMsg(msg: String): Component = msg.asMiniMessage()
fun escapeMiniMessageTags(text: String): String = text.escapeMiniMessage()
fun getLegacyFromComponent(component: Component): String = component.toLegacyAmpersand()
fun getPlainTextFromComponent(component: Component): String = component.toPlainText()