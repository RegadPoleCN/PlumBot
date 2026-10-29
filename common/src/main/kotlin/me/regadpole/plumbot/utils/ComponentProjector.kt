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
import net.kyori.adventure.text.KeybindComponent
import net.kyori.adventure.text.NBTComponent
import net.kyori.adventure.text.ScoreComponent
import net.kyori.adventure.text.SelectorComponent
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.TranslationArgument
import net.kyori.adventure.text.VirtualComponent
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import java.util.Locale

/**
 * Adventure 组件到「可读文本」的唯一投影器。
 *
 * 背景：默认 [ComponentFlattener.basic] 对 [TranslatableComponent] 只输出 `fallback ?: key`，
 * 参数会丢失；adventure 5.x 的 PlainText 序列化器还会对 [ScoreComponent] 抛异常。Velocity
 * 官方命令大量使用 translatable（如 `commands.give.success.single`），远程回显必须统一处理。
 *
 * 处理顺序：[GlobalTranslator.render] 用宿主翻译源渲染 → 自定义 flattener 兜住剩余节点 →
 * 三个出口（`§` 样式 / 无样式 / `&` 样式）。只依赖 adventure-api 与 legacy serializer。
 */
object ComponentProjector {

    /** 默认翻译语言。宿主没有对应翻译时会自动回落到 key(args)/fallback。 */
    val DEFAULT_LOCALE: Locale = Locale.SIMPLIFIED_CHINESE

    private val flattener: ComponentFlattener = ComponentFlattener.builder()
        .mapper(TextComponent::class.java) { it.content() }
        .mapper(TranslatableComponent::class.java) { translatableText(it) }
        .mapper(ScoreComponent::class.java) { scoreText(it) }
        .mapper(SelectorComponent::class.java) { it.pattern() }
        .mapper(KeybindComponent::class.java) { "按键: ${it.keybind()}" }
        .mapper(NBTComponent::class.java) { "<nbt:${it.nbtPath()}>" }
        .unknownMapper { unsupportedText(it) }
        .build()

    private val sectionSerializer: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character('§')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .flattener(flattener)
        .build()

    private val ampersandSerializer: LegacyComponentSerializer = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .flattener(flattener)
        .build()

    /** 原版翻译键解析器：GlobalTranslator 不含 Paper 的 `commands.*` 翻译源，flattener 之前先试；测试可替换。 */
    @Volatile
    internal var translationResolver: MinecraftTranslationResolver? = MinecraftTranslationSource

    /** 预热序列化器、flattener 与翻译解析链的类加载。幂等、只读，失败不影响后续投影。 */
    @JvmStatic
    fun warmUp() {
        runCatching { project(Component.text("warmup")) }
    }

    /** 带 `§` 样式，供卡片正文 / AWT 渲染。 */
    @JvmStatic
    @JvmOverloads
    fun project(component: Component, locale: Locale = DEFAULT_LOCALE): String {
        val rendered = renderTranslatables(component, locale)
        return sectionSerializer.serialize(rendered)
    }

    /** 无样式，供群聊纯文本回退。 */
    @JvmStatic
    @JvmOverloads
    fun projectPlain(component: Component, locale: Locale = DEFAULT_LOCALE): String {
        return stripSectionCodes(project(component, locale))
    }

    /** `&` 样式，仅用于仍只认识 `&` 的旧渲染路径。 */
    @JvmStatic
    @JvmOverloads
    fun projectLegacyAmpersand(component: Component, locale: Locale = DEFAULT_LOCALE): String {
        val rendered = renderTranslatables(component, locale)
        return ampersandSerializer.serialize(rendered)
    }

    /** 尝试用宿主的 [GlobalTranslator] 渲染；没有翻译源时保持原组件。 */
    private fun renderTranslatables(component: Component, locale: Locale): Component {
        return try {
            GlobalTranslator.render(component, locale)
        } catch (_: Throwable) {
            // 宿主可能没有注册任何翻译源；渲染失败不应让回显整体失败。
            component
        }
    }

    private fun translatableText(component: TranslatableComponent): String {
        val args = component.arguments().map(::argumentText)
        val key = component.key()

        // 1) 服务端原版翻译（Paper 不会把 commands.* 注册进 GlobalTranslator）。
        val translated = translationResolver?.resolve(key, args)
        if (!translated.isNullOrEmpty()) return translated

        // 2) Adventure 载荷自带的 fallback。
        val fallback = component.fallback()
        if (!fallback.isNullOrEmpty()) {
            return formatFallback(fallback, args)
        }

        // 3) 绝不只输出裸 key：至少把参数带上，方便使用者判断是哪条消息缺翻译。
        return buildString {
            append("[未翻译] ")
            append(key)
            if (args.isNotEmpty()) {
                append(args.joinToString(", ", "(", ")"))
            }
        }
    }

    private fun formatFallback(fallback: String, args: List<String>): String {
        return MinecraftTranslationSource.formatTemplate(fallback, args)
    }

    private fun argumentText(argument: TranslationArgument): String {
        return when (val value = argument.value()) {
            null -> ""
            is Component -> projectPlain(value)
            is net.kyori.adventure.text.ComponentLike -> projectPlain(value.asComponent())
            else -> value.toString()
        }
    }

    private fun scoreText(component: ScoreComponent): String {
        val value = component.value() ?: "?"
        return "score(${component.name()})=$value"
    }

    private fun unsupportedText(component: Component): String {
        return when (component) {
            is NBTComponent<*, *> -> "<nbt:${component.nbtPath()}>"
            is VirtualComponent -> "<virtual>"
            else -> "<unsupported:${component.javaClass.simpleName}>"
        }
    }

    private val sectionCodePattern = Regex(
        "§x(?:§[0-9A-Fa-f]){6}|§#[0-9A-Fa-f]{6}|§[0-9A-FK-ORa-fk-or]"
    )

    /** 剥除 `§` 样式码（含 `§x§r§r§g§g§b§b` 十六进制形式）。 */
    @JvmStatic
    fun stripSectionCodes(text: String): String = sectionCodePattern.replace(text, "")

    private val ansiSgr = Regex("\u001B\\[([0-9;]*)m")
    // ANSI 与 Minecraft 的 16 色不是同一套编号：ANSI 32 是“绿”，对应 §a 而不是 §2。
    private val ANSI_BASIC = charArrayOf('0', 'c', 'a', 'e', '9', 'd', 'b', 'f')
    private val ANSI_BRIGHT = charArrayOf('8', 'c', 'a', 'e', '9', 'd', 'b', 'f')

    private val ansiCsi = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
    private val ansiOsc = Regex("\u001B\\][^\u0007]*(?:\u0007|\u001B\\\\)")

    /**
     * 把控制台 ANSI 颜色转换为 Minecraft `§` 颜色（保留真实色彩）。只处理 SGR；其余
     * CSI/OSC 控制序列直接移除，避免把终端控制符带进群消息或渲染卡片。
     */
    @JvmStatic
    fun ansiToSection(input: String): String {
        if (input.indexOf('\u001B') < 0) return input
        val converted = ansiSgr.replace(input) { match ->
            sgrToSection(match.groupValues[1])
        }
        return ansiCsi.replace(ansiOsc.replace(converted, ""), "")
    }

    private fun sgrToSection(params: String): String {
        if (params.isEmpty()) return "§r"
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        val out = StringBuilder()
        var i = 0
        while (i < codes.size) {
            val code = codes[i]
            when {
                code == 0 -> out.append("§r")
                code == 1 -> out.append("§l")
                code == 3 -> out.append("§o")
                code == 4 -> out.append("§n")
                code == 9 -> out.append("§m")
                code == 22 || code == 23 || code == 24 || code == 29 -> out.append("§r")
                code == 39 -> out.append("§r")
                code in 30..37 -> out.append('§').append(ANSI_BASIC[code - 30])
                code in 90..97 -> out.append('§').append(ANSI_BRIGHT[code - 90])
                code == 38 && i + 2 < codes.size && codes[i + 1] == 5 -> {
                    val palette = codes[i + 2]
                    if (palette in 0..7) {
                        out.append('§').append(ANSI_BASIC[palette])
                    } else if (palette in 8..15) {
                        out.append('§').append(ANSI_BRIGHT[palette - 8])
                    }
                    i += 2
                }
                code == 38 && i + 4 < codes.size && codes[i + 1] == 2 -> {
                    out.append("§x")
                    for (component in listOf(codes[i + 2], codes[i + 3], codes[i + 4])) {
                        val hex = component.coerceIn(0, 255).toString(16).padStart(2, '0')
                        out.append('§').append(hex[0]).append('§').append(hex[1])
                    }
                    i += 4
                }
                // 背景色/其它 SGR 在 Minecraft 传统颜色中没有等价表示，忽略。
                else -> Unit
            }
            i++
        }
        return out.toString()
    }
}
