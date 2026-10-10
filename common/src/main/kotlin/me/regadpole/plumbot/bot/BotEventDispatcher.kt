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
        // 内置 handler 与"第三方监听器"必须互相隔离：内置 handler 抛异常（例如数据库挂了）
        // 不能阻止事件送达注册方，否则一个业务错误会让所有扩展静默收不到消息。
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
        // 先失效适配器的本地成员缓存，再走业务处理。
        //
        // 放在这里（而不是各适配器的监听器里）是因为三个监听器都汇聚到本方法：
        // 只需一处即可对所有适配器生效，也不会因将来新增适配器而遗漏。
        // 背景：此前 OneBot 的监听器自己失效了缓存，两个 MiraiMC 监听器没有，
        // 于是 MiraiMC 下退群者会在缓存 TTL（30 分钟）内继续被判为"仍在群内"。
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
