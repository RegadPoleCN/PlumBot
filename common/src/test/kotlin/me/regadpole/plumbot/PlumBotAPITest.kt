package me.regadpole.plumbot

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tests that exercise the public API surface without depending on the Bukkit
 * runtime or a live `PlumBot` attachment. They verify:
 *  - `PlumBotAPI` is an `object` exposing the singleton.
 *  - `getInstance()` returns the singleton regardless of attach state.
 *  - `sendXxxMessage(...)` returns `false` when no bot is currently loaded
 *    (does NOT throw to the caller).
 *  - Listener registration / deregistration is balanced.
 */
class PlumBotAPITest {

    @BeforeTest
    fun resetState() {
        try {
            PlumBotAPI.detach()
        } catch (_: Throwable) {
            // detach is safe to call even if never attached.
        }
    }

    @AfterTest
    fun tearDown() {
        PlumBotAPI.detach()
    }

    @Test
    fun `getInstance returns singleton regardless of attach`() {
        val a = PlumBotAPI.getInstance()
        val b = PlumBotAPI.getInstance()
        assertTrue(a === b, "PlumBotAPI must be a singleton object")
        assertNotNull(a)
    }

    @Test
    fun `sendGroupMessage throws BotNotReadyException when no bot loaded`() {
        PlumBotAPI.detach()
        kotlin.test.assertFailsWith<me.regadpole.plumbot.api.exception.BotNotReadyException> {
            PlumBotAPI.sendGroupMessage(123L, "hi")
        }
        kotlin.test.assertFailsWith<me.regadpole.plumbot.api.exception.BotNotReadyException> {
            PlumBotAPI.sendUserMessage(456L, "hi")
        }
        kotlin.test.assertFailsWith<me.regadpole.plumbot.api.exception.BotNotReadyException> {
            PlumBotAPI.sendGroupMessageWithImage(123L, "hi")
        }
        kotlin.test.assertFailsWith<me.regadpole.plumbot.api.exception.BotNotReadyException> {
            PlumBotAPI.sendUserMessageWithImage(456L, "hi")
        }
    }

    @Test
    fun `getBotOrNull returns null when no bot loaded`() {
        PlumBotAPI.detach()
        assertNull(PlumBotAPI.getBotOrNull())
        try {
            PlumBotAPI.getBot()
            fail("getBot() should have thrown when no bot is loaded")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `subscribeGroupMessages close unregisters listener`() {
        PlumBotAPI.detach() // no plugin, no harm; just defensive.
        // Since we have no dispatcher attached plugin we need to use the
        // dispatcher directly to verify wiring. The dispatcher is internal,
        // but its registration is observable through `close()` semantics.
        var invocations = 0
        val handle = PlumBotAPI.subscribeGroupMessage { _ ->
            invocations++
        }
        // Call the dispatcher directly (same package — these tests share the common sources).
        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations, "first dispatch must hit the listener")
        handle.close()
        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 1L, 2L)
        assertEquals(1, invocations, "second dispatch must NOT hit the listener after close")
        // close() is idempotent.
        handle.close()
        handle.close()
    }

    @Test
    fun `subscribeGroupMessages handler exception does not throw to caller`() {
        var throwCount = 0
        PlumBotAPI.subscribeGroupMessage { _ ->
            throwCount++
            throw RuntimeException("boom")
        }
        // The dispatcher swallows exceptions.
        BotEventDispatcherAccessor.dispatchGroupMessage("hello", 7L, 8L)
        assertEquals(1, throwCount)
    }
}
