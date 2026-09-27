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

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotExtensionRegistryTest {

    private class FakePlugin(override val name: String, override val version: String = "1.0.0") : Plugin {
        override val isEnabled: Boolean = true
        override fun equals(other: Any?): Boolean = other is FakePlugin && other.name == name
        override fun hashCode(): Int = name.hashCode()
        override fun toString(): String = "FakePlugin($name)"
    }

    private class FakeFactory(
        type: String,
        private val platformSupports: PlatformType? = null,
    ) : BotFactory {
        override val metadata: BotAdapterMetadata = BotAdapterMetadata(
            type = type,
            supportedPlatforms = if (platformSupports != null) setOf(platformSupports) else emptySet(),
        )
        override fun create(context: PlatformContext): IBot = error("not used in this test")
    }

    @BeforeTest
    fun reset() {
        BotProvider.clearFactories()
    }

    @AfterTest
    fun tearDown() {
        BotProvider.clearFactories()
    }

    @Test
    fun `built-in factory refuses external registration with same type`() {
        val oneBot = FakeFactory("onebotx")
        BotProvider.registerFactory(oneBot)
        val pluginA = FakePlugin("pluginA")
        val registry = DefaultBotExtensionRegistry(BotProvider)
        kotlin.test.assertFailsWith<me.regadpole.plumbot.api.exception.AdapterRegistrationException> {
            registry.registerExternalFactory(FakeFactory("onebotx"), pluginA)
        }
        assertTrue("onebotx" in BotProvider.availableAdapters().map { it.type })
    }

    @Test
    fun `same plugin can register and unregister its own factory`() {
        val pluginA = FakePlugin("pluginA")
        val factory = FakeFactory("mycool")
        val registry = DefaultBotExtensionRegistry(BotProvider)
        assertTrue(registry.registerExternalFactory(factory, pluginA))
        assertEquals(listOf("mycool" to pluginA), registry.snapshot())
        assertTrue(registry.unregisterExternalFactory("mycool", pluginA))
        assertEquals(emptyList(), registry.snapshot())
    }

    @Test
    fun `unregisterAllFor cleans only the given plugin's registrations`() {
        val pluginA = FakePlugin("pluginA")
        val pluginB = FakePlugin("pluginB")
        val factoryA = FakeFactory("factA")
        val factoryB = FakeFactory("factB")
        val registry = DefaultBotExtensionRegistry(BotProvider)
        registry.registerExternalFactory(factoryA, pluginA)
        registry.registerExternalFactory(factoryB, pluginB)
        val removed = registry.unregisterAllFor(pluginA)
        assertEquals(1, removed)
        assertEquals(listOf("factb" to pluginB), registry.snapshot())
    }

    @Test
    fun `unregisterAllFor on unknown plugin returns zero`() {
        val registry = DefaultBotExtensionRegistry(BotProvider)
        val removed = registry.unregisterAllFor(FakePlugin("nope"))
        assertEquals(0, removed)
    }
}
