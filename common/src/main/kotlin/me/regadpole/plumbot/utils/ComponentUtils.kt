/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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