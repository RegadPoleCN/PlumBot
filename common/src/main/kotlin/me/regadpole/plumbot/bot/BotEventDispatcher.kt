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

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.Plugin
import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.internal.LogLevel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** bot 侧入站事件（群消息、群成员减少）的统一分发入口。 */
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
        // 内置 handler 与第三方监听器互相隔离：内置 handler 抛异常（如数据库挂了）不能让事件送不到注册方，否则一次业务错误就会让所有扩展静默收不到消息。
        runCatching { BotProvider.getBot()?.handler?.onGroupMessage(messageRaw, groupId, senderId) }
            .onFailure { logListenerFailure("内置 GroupMessage handler", it) }

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
        val bot = BotProvider.getBot()
        // 先失效适配器的本地成员缓存，再走业务处理。所有适配器的监听器都汇聚到本方法，一处失效
        // 即可对全部生效，也不会因新增适配器而遗漏；若交给各适配器自行失效，漏掉一处就会让退群者
        // 在缓存 TTL（30 分钟）内仍被判为"仍在群内"。
        runCatching { bot?.invalidateGroupMember(groupId, userId) }
            .onFailure { logListenerFailure("成员缓存失效", it) }
        runCatching { bot?.handler?.onUserDecrease(groupId, userId) }
            .onFailure { logListenerFailure("内置 GroupMemberDecrease handler", it) }

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
