package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.event.UserDecreaseEvent
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class BotEventDispatcherTest {

    @BeforeTest
    fun reset() {
        BotEventDispatcher.clearAllListeners()
        try { PlumBotAPI.detach() } catch (_: Throwable) {}
    }

    @AfterTest
    fun tearDown() {
        BotEventDispatcher.clearAllListeners()
        try { PlumBotAPI.detach() } catch (_: Throwable) {}
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
        BotEventDispatcher.registerUserDecreaseHandler { fail("should not fire") }
        BotEventDispatcher.clearAllListeners()
        BotEventDispatcher.dispatchGroupMessage("m", 1L, 2L)
        BotEventDispatcher.dispatchUserDecrease(1L, 2L)
    }

    @Test
    fun `public API subscribeGroupMessages delegates`() {
        var count = 0
        val handle = PlumBotAPI.subscribeGroupMessages { _: GroupMessageEvent -> count++ }
        BotEventDispatcher.dispatchGroupMessage("hello", 10L, 20L)
        assertEquals(1, count)
        handle.close()
    }

    @Test
    fun `public API subscribeUserDecrease delegates`() {
        var count = 0
        val handle = PlumBotAPI.subscribeUserDecrease { _: UserDecreaseEvent -> count++ }
        BotEventDispatcher.dispatchUserDecrease(10L, 20L)
        assertEquals(1, count)
        handle.close()
    }
}
