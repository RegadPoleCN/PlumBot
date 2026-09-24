package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.internal.LogLevel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Unified event dispatcher for bot-side incoming events (group messages,
 * group member decrease).
 */
object BotEventDispatcher {

    private val groupMessageHandlers: MutableList<(GroupMessageEvent) -> Unit> = CopyOnWriteArrayList()
    private val memberDecreaseHandlers: MutableList<(GroupMemberDecreaseEvent) -> Unit> = CopyOnWriteArrayList()
    private val pluginBindings: MutableMap<Plugin, MutableList<ListenerHandle>> = ConcurrentHashMap()

    @Volatile
    var attachedPlugin: PlumBot? = null

    fun registerGroupMessageHandler(handler: (GroupMessageEvent) -> Unit): ListenerHandle {
        groupMessageHandlers.add(handler)
        return ListenerHandle {
            groupMessageHandlers.remove(handler)
        }
    }

    fun registerMemberDecreaseHandler(handler: (GroupMemberDecreaseEvent) -> Unit): ListenerHandle {
        memberDecreaseHandlers.add(handler)
        return ListenerHandle {
            memberDecreaseHandlers.remove(handler)
        }
    }

    fun bindPluginLifecycle(plugin: Plugin, handle: ListenerHandle) {
        pluginBindings.computeIfAbsent(plugin) { CopyOnWriteArrayList() }.add(handle)
    }

    fun unregisterAllFor(plugin: Plugin) {
        val handles = pluginBindings.remove(plugin) ?: return
        handles.forEach { it.close() }
    }

    fun clearAllListeners() {
        groupMessageHandlers.clear()
        memberDecreaseHandlers.clear()
        pluginBindings.clear()
    }

    fun dispatchGroupMessage(messageRaw: String, groupId: Long, senderId: Long) {
        BotProvider.getBot()?.handler?.onGroupMessage(messageRaw, groupId, senderId)

        val event = GroupMessageEvent(
            botId = currentBotId(),
            groupId = groupId,
            userId = senderId,
            message = messageRaw,
            timestamp = System.currentTimeMillis(),
        )
        dispatchTo(groupMessageHandlers, event, "GroupMessage")
    }

    fun dispatchUserDecrease(groupId: Long, userId: Long) {
        BotProvider.getBot()?.handler?.onUserDecrease(groupId, userId)

        val event = GroupMemberDecreaseEvent(
            botId = currentBotId(),
            groupId = groupId,
            userId = userId,
            timestamp = System.currentTimeMillis(),
        )
        dispatchTo(memberDecreaseHandlers, event, "GroupMemberDecrease")
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
        val plugin = attachedPlugin
        val msg = "BotEventDispatcher $label listener threw: ${e.message ?: e.javaClass.simpleName}"
        if (plugin != null) {
            plugin.log(LogLevel.WARN, msg)
        } else {
            System.err.println(msg)
        }
    }
}
