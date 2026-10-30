// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * [MinecraftTranslationSource] 的解析契约（无 NMS 环境）。
 *
 * 通过替换 [ComponentProjector.translationResolver] 模拟服务端翻译源：命中、未命中、
 * 参数投影与格式化失败都必须可观察，且不得吞掉 key 或参数。
 */
class MinecraftTranslationSourceTest {

    @Test
    fun `commands list players resolves without untranslated marker`() {
        val resolver = MinecraftTranslationResolver { key, args ->
            if (key == "commands.list.players") {
                MinecraftTranslationSource.formatTemplate(
                    "There are %s of a max of %s players online: %s",
                    args
                )
            } else {
                null
            }
        }

        withResolver(resolver) {
            val text = ComponentProjector.projectPlain(
                Component.translatable(
                    "commands.list.players",
                    Component.text("0"),
                    Component.text("20"),
                    Component.text("")
                )
            )

            assertFalse(text.contains("[未翻译]"), text)
            assertEquals("There are 0 of a max of 20 players online:", text.trim())
        }
    }

    @Test
    fun `unknown key still keeps key and arguments`() {
        withResolver(MinecraftTranslationResolver { _, _ -> null }) {
            val text = ComponentProjector.projectPlain(
                Component.translatable("plumbot.missing.key", Component.text("Steve"))
            )

            assertEquals("[未翻译] plumbot.missing.key(Steve)", text)
        }
    }

    @Test
    fun `component arguments are projected before formatting`() {
        val resolver = MinecraftTranslationResolver { _, args ->
            MinecraftTranslationSource.formatTemplate("Hello %s", args)
        }

        withResolver(resolver) {
            val text = ComponentProjector.projectPlain(
                Component.translatable(
                    "plumbot.greet",
                    Component.text("Steve", NamedTextColor.GREEN)
                )
            )

            assertEquals("Hello Steve", text)
            assertFalse(text.contains("§"), text)
        }
    }

    @Test
    fun `key only template is treated as a miss`() {
        assertNull(
            MinecraftTranslationSource.resolveTemplate(
                "commands.list.players",
                "commands.list.players",
                listOf("0", "20", "")
            )
        )
    }

    @Test
    fun `missing template is a miss`() {
        assertNull(MinecraftTranslationSource.resolveTemplate("plumbot.missing", null, emptyList()))
    }

    @Test
    fun `format failure keeps template and all arguments`() {
        val text = MinecraftTranslationSource.formatTemplate("bad %d", listOf("not-a-number"))

        assertEquals("bad %d (not-a-number)", text)
    }

    @Test
    fun `status is always readable and never throws without nms`() {
        val status = MinecraftTranslationSource.status()

        assertTrue(status.isNotBlank(), status)
        assertTrue(status.startsWith("可用") || status.startsWith("不可用"), status)
    }

    private fun <T> withResolver(resolver: MinecraftTranslationResolver, block: () -> T): T {
        val previous = ComponentProjector.translationResolver
        ComponentProjector.translationResolver = resolver
        return try {
            block()
        } finally {
            ComponentProjector.translationResolver = previous
        }
    }
}
