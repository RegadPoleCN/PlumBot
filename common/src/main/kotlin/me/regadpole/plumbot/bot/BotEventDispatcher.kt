package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBotAPI
import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.event.UserDecreaseEvent
import me.regadpole.plumbot.internal.LogLevel
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unified event dispatcher for bot-side incoming events (group messages,
 * user decrease).
 *
 * **Stability**: This object is part of the internal framework surface. Public
 * third-party listeners **MUST NOT** call it directly; use
 * [PlumBotAPI.subscribeGroupMessages] / [PlumBotAPI.subscribeUserDecrease]
 * which delegate to [registerGroupMessageHandler] / [registerUserDecreaseHandler].
 *
 * The two public dispatch methods (`dispatchGroupMessage` / `dispatchUserDecrease`)
 * are called by the bot adapter listeners (`OneBotListener`,
 * `MiraiMCListener`) to forward raw adapter events to the framework's
 * [me.regadpole.plumbot.listener.BotHandler] chain.
 *
 * Listener registry semantics:
 *  - Thread-safe.
 *  - Exceptions thrown by a listener are isolated and logged; other listeners
 *    still run.
 *  - All registered listeners are cleared on [unloadBot].
 */
object BotEventDispatcher {

    private val groupMessageHandlers: MutableList<(GroupMessageEvent) -> Unit> = CopyOnWriteArrayList()
    private val userDecreaseHandlers: MutableList<(UserDecreaseEvent) -> Unit> = CopyOnWriteArrayList()

    /**
     * Register a public-API listener for group messages. Returns a
     * [ListenerHandle] whose [ListenerHandle.close] unregisters exactly this
     * listener. Idempotent close is guaranteed.
     */
    fun registerGroupMessageHandler(handler: (GroupMessageEvent) -> Unit): ListenerHandle {
        groupMessageHandlers.add(handler)
        return ListenerHandle {
            groupMessageHandlers.remove(handler)
        }
    }

    /**
     * Register a public-API listener for user decrease events. Symmetric to
     * [registerGroupMessageHandler].
     */
    fun registerUserDecreaseHandler(handler: (UserDecreaseEvent) -> Unit): ListenerHandle {
        userDecreaseHandlers.add(handler)
        return ListenerHandle {
            userDecreaseHandlers.remove(handler)
        }
    }

    /**
     * Drop every registered listener. Called by [BotProvider.unloadBot] via
     * [clearAllListeners].
     */
    fun clearAllListeners() {
        groupMessageHandlers.clear()
        userDecreaseHandlers.clear()
    }

    // ------------------------------------------------------------------
    // Internal dispatch entry-points. Only bot adapter listeners call these.
    // ------------------------------------------------------------------

    /**
     * Dispatch an inbound group message into the framework's handler chain.
     *
     * @param messageRaw the raw message text (may contain color codes; will be
     *                   forwarded as-is to listeners).
     * @param groupId the QQ (or equivalent) group id.
     * @param senderId the message sender id.
     */
    fun dispatchGroupMessage(messageRaw: String, groupId: Long, senderId: Long) {
        // Preserve existing behavior: forward to BotHandler if present.
        BotProvider.getBot()?.handler?.onGroupMessage(messageRaw, groupId, senderId)

        // Public-API fan-out.
        val event = GroupMessageEvent(
            botId = currentBotId(),
            groupId = groupId,
            userId = senderId,
            message = messageRaw,
            timestamp = System.currentTimeMillis(),
        )
        dispatchTo(groupMessageHandlers, event, "GroupMessage")
    }

    /**
     * Dispatch an inbound user decrease event.
     */
    fun dispatchUserDecrease(groupId: Long, userId: Long) {
        BotProvider.getBot()?.handler?.onUserDecrease(groupId, userId)

        val event = UserDecreaseEvent(
            botId = currentBotId(),
            groupId = groupId,
            userId = userId,
            timestamp = System.currentTimeMillis(),
        )
        dispatchTo(userDecreaseHandlers, event, "UserDecrease")
    }

    private fun <E> dispatchTo(
        handlers: List<(E) -> Unit>,
        event: E,
        label: String,
    ) {
        for (handler in handlers) {
            try {
                handler(event)
            } catch (e: Throwable) {
                logListenerFailure(label, e)
            }
        }
    }

    private fun currentBotId(): String =
        BotProvider.getBot()?.metadata?.type ?: "unknown"

    private fun logListenerFailure(label: String, e: Throwable) {
        val plugin = PlumBotAPI.getAttachedPlugin()
        val msg = "BotEventDispatcher $label listener threw: ${e.message ?: e.javaClass.simpleName}"
        if (plugin != null) {
            plugin.log(LogLevel.WARN, msg)
        } else {
            System.err.println(msg)
        }
    }
}
