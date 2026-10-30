// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.utils

import java.text.MessageFormat
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.translation.GlobalTranslator
import net.kyori.adventure.translation.TranslationRegistry

/**
 * [ComponentProjector] 的投影契约：翻译、未翻译兜底、样式出口与 ANSI 转换。
 *
 * 用真实的 [GlobalTranslator] 注册表验证"宿主翻译源优先生效"，其余用例覆盖组件节点
 * 不被吞掉、样式码互不串用。
 */
class ComponentProjectorTest {

    private lateinit var registry: TranslationRegistry

    @BeforeTest
    fun installTranslationSource() {
        registry = TranslationRegistry.create(Key.key("plumbot", "component-projector-test"))
        registry.register("plumbot.greet", Locale.US, MessageFormat("Hello {0}"))
        registry.defaultLocale(Locale.US)
        GlobalTranslator.translator().addSource(registry)
    }

    @AfterTest
    fun removeTranslationSource() {
        GlobalTranslator.translator().removeSource(registry)
    }

    @Test
    fun `translatable is rendered by the host translator`() {
        val component = Component.translatable("plumbot.greet", Component.text("Steve"))

        assertEquals("Hello Steve", ComponentProjector.projectPlain(component, Locale.US))
    }

    @Test
    fun `untranslated key keeps key and arguments instead of disappearing`() {
        val component = Component.translatable("plumbot.missing.key", Component.text("Steve"))

        val text = ComponentProjector.projectPlain(component)

        assertTrue(text.contains("[未翻译]"), text)
        assertTrue(text.contains("plumbot.missing.key"), text)
        assertTrue(text.contains("Steve"), text)
    }

    @Test
    fun `fallback is formatted with arguments`() {
        val component = Component.translatable(
            "plumbot.missing.with.fallback",
            "Gave %s to %s",
            Component.text("1"),
            Component.text("Steve")
        )

        assertEquals("Gave 1 to Steve", ComponentProjector.projectPlain(component))
    }

    @Test
    fun `plain text keeps text and drops styling`() {
        val component = Component.text("hello", NamedTextColor.GREEN)

        assertTrue(ComponentProjector.project(component).contains("§ahello"), ComponentProjector.project(component))
        assertEquals("hello", ComponentProjector.projectPlain(component))
    }

    @Test
    fun `mixed text and translatable are both preserved`() {
        val component = Component.text("A ")
            .append(Component.translatable("plumbot.greet", Component.text("B")))

        assertEquals("A Hello B", ComponentProjector.projectPlain(component, Locale.US))
    }

    @Test
    fun `score keybind and selector are not swallowed`() {
        val text = ComponentProjector.projectPlain(
            Component.text("s=")
                .append(Component.score("Steve", "points", "42"))
                .append(Component.text(" k="))
                .append(Component.keybind("key.jump"))
                .append(Component.text(" p="))
                .append(Component.selector("@a"))
        )

        assertTrue(text.contains("score(Steve)=42"), text)
        assertTrue(text.contains("key.jump"), text)
        assertTrue(text.contains("@a"), text)
        assertFalse(text.contains("[unsupported"), text)
    }

    @Test
    fun `legacy ampersand output uses ampersand`() {
        val text = ComponentProjector.projectLegacyAmpersand(Component.text("hi", NamedTextColor.GREEN))

        assertTrue(text.contains("&ahi"), text)
        assertFalse(text.contains("§a"), text)
    }

    @Test
    fun `ansi colors become section codes`() {
        assertEquals("§aOK§r", ComponentProjector.ansiToSection("\u001B[32mOK\u001B[0m"))
        assertEquals(
            "§x§f§f§0§0§8§0X",
            ComponentProjector.ansiToSection("\u001B[38;2;255;0;128mX")
        )
    }

    @Test
    fun `non sgr ansi sequences are removed`() {
        assertEquals("x", ComponentProjector.ansiToSection("\u001B[2Jx"))
    }

    @Test
    fun `section codes are stripped including hex`() {
        assertEquals("AB", ComponentProjector.stripSectionCodes("§aA§x§f§f§0§0§0§0B"))
    }

    @Test
    fun `stripMinecraftFormatting handles amp and section prefixes`() {
        assertEquals("Hello World", "&aHello &lWorld".stripMinecraftFormatting())
        assertEquals("Hello World", "§aHello §lWorld".stripMinecraftFormatting())
    }
}
