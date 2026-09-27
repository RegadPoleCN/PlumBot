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
import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BotEventDispatcherTest {

    private class DummyPlugin(override val name: String = "DummyPlugin") : Plugin {
        override val version: String = "1.0.0"
        override val isEnabled: Boolean = true
    }

    @BeforeTest
    fun reset() {
        BotEventDispatcher.clearAllListeners()
    }

    @AfterTest
    fun tearDown() {
        BotEventDispatcher.clearAllListeners()
    }

    @Test
    fun `register unregister round-trip`() {
        var fired = 0
        val handle = BotEventDispatcher.registerGroupMessageHandler { fired++ }
        BotEventDispatcher.dispatchGroupMessage("m", 1L, 2L)
        assertEquals(1, fired)
        handle.close()
        BotEventDispatcher.dispatchGroupMessage("m", 1L, 2L)
        assertEquals(1, fired)
    }

    @Test
    fun `handler exception is isolated`() {
        BotEventDispatcher.registerGroupMessageHandler { throw IllegalStateException("boom") }
        var secondFired = false
        BotEventDispatcher.registerGroupMessageHandler { secondFired = true }
        BotEventDispatcher.dispatchGroupMessage("m", 1L, 2L)
        assertTrue(secondFired, "second listener should still execute even if first throws")
    }

    @Test
    fun `clearAllListeners drops everything`() {
        BotEventDispatcher.registerGroupMessageHandler { fail("should not fire") }
        BotEventDispatcher.registerMemberDecreaseHandler { fail("should not fire") }
        BotEventDispatcher.clearAllListeners()
        BotEventDispatcher.dispatchGroupMessage("m", 1L, 2L)
        BotEventDispatcher.dispatchUserDecrease(1L, 2L)
    }

    @Test
    fun `bindPluginLifecycle unregisters on plugin disable`() {
        val plugin = DummyPlugin()
        var count = 0
        val handle = BotEventDispatcher.registerGroupMessageHandler { count++ }
        BotEventDispatcher.bindPluginLifecycle(plugin, handle)

        BotEventDispatcher.dispatchGroupMessage("hello", 10L, 20L)
        assertEquals(1, count)

        BotEventDispatcher.unregisterAllFor(plugin)

        BotEventDispatcher.dispatchGroupMessage("hello", 10L, 20L)
        assertEquals(1, count)
    }
}
