// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** 将 MiniMessage 字符串反序列化为 Component */
fun String.asMiniMessage(): Component = MiniMessage.miniMessage().deserialize(this)

/** 转义 MiniMessage 标签 */
fun String.escapeMiniMessage(): String = MiniMessage.miniMessage().escapeTags(this)

/** Component 转 & 颜色字符串（翻译键会先经过 GlobalTranslator 渲染）。 */
fun Component.toLegacyAmpersand(): String = ComponentProjector.projectLegacyAmpersand(this)

/** 提取 Component 中的所有纯文本，完全剥除颜色和样式；translatable/score/keybind 等节点不会被吞掉。 */
fun Component.toPlainText(): String = ComponentProjector.projectPlain(this)

/** 剥除传统 Minecraft 格式码（同时支持 & 与 § 两种前缀）。 */
fun String.stripMinecraftFormatting(): String {
    val component = if (this.contains('§')) {
        LegacyComponentSerializer.legacySection().deserialize(this)
    } else {
        LegacyComponentSerializer.legacyAmpersand().deserialize(this)
    }
    return ComponentProjector.projectPlain(component)
}

// 保持既有顶层函数无缝兼容
fun getComponentFromMiniMsg(msg: String): Component = msg.asMiniMessage()
fun escapeMiniMessageTags(text: String): String = text.escapeMiniMessage()
fun getLegacyFromComponent(component: Component): String = component.toLegacyAmpersand()
fun getPlainTextFromComponent(component: Component): String = component.toPlainText()