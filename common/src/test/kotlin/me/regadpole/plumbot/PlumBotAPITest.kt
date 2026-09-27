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

package me.regadpole.plumbot

import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.PlumBotApiProvider
import me.regadpole.plumbot.api.exception.BotNotReadyException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        // Subscribe via the dispatcher directly using plugin binding
        val handle = me.regadpole.plumbot.bot.BotEventDispatcher.registerGroupMessageHandler {
            invocations++
        }
        me.regadpole.plumbot.bot.BotEventDispatcher.bindPluginLifecycle(plugin, handle)

        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations)

        // Simulate external plugin unload
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
        // Must not throw
        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 7L, 8L)
    }
}
