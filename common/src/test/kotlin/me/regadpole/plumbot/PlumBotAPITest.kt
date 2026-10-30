// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot

import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.PlumBotApiProvider
import kotlin.test.*

class PlumBotAPITest {

    private class TestPlugin(override val name: String = "TestPlugin") : Plugin {
        override val version: String = "1.0.0"
        override val isEnabled: Boolean = true
    }

    @BeforeTest
    fun resetState() {
        PlumBotApiProvider.unregister()
    }

    @AfterTest
    fun tearDown() {
        PlumBotApiProvider.unregister()
    }

    @Test
    fun `PlumBotAPI get throws when not attached`() {
        PlumBotApiProvider.unregister()
        assertFailsWith<IllegalStateException> {
            PlumBotAPI.get()
        }
    }

    @Test
    fun `subscribeGroupMessage and unregisterAllFor cleans listeners on plugin disable`() {
        val plugin = TestPlugin()
        var invocations = 0
        val handle = me.regadpole.plumbot.bot.BotEventDispatcher.registerGroupMessageHandler {
            invocations++
        }
        me.regadpole.plumbot.bot.BotEventDispatcher.bindPluginLifecycle(plugin, handle)

        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations)

        me.regadpole.plumbot.bot.BotEventDispatcher.unregisterAllFor(plugin)

        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations, "Listener must be unregistered when plugin is unloaded")
    }

    @Test
    fun `manual close on listener handle is idempotent`() {
        var invocations = 0
        val handle = me.regadpole.plumbot.bot.BotEventDispatcher.registerGroupMessageHandler {
            invocations++
        }

        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations)

        handle.close()
        handle.close()

        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations)
    }

    @Test
    fun `listener exception does not throw to caller`() {
        me.regadpole.plumbot.bot.BotEventDispatcher.registerGroupMessageHandler {
            throw RuntimeException("boom")
        }
        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 7L, 8L)
    }
}
