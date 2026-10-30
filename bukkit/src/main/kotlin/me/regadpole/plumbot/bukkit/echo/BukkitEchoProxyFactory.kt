// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.md_5.bungee.api.chat.BaseComponent
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 动态代理 ConsoleCommandSender，拦住插件指令的反馈。
 *
 * 只用于 plugin_command / proxy_sender 两个通道：Paper 的 CraftServer 可能把 sender 换成
 * 内部 CommandSourceStack（proxy_sender 因此在 Paper 上不可靠），但直接调用
 * PluginCommand.execute(proxy, ...) 仍能收到插件对 sender 的 sendMessage。
 *
 * 拦截方法按名字匹配而非精确签名，以兼容 Paper 新增的 sendRichMessage 等默认方法；
 * 其余方法委托真实 console sender，InvocationTargetException 解包后原样抛出。
 */
internal object BukkitEchoProxyFactory {

    fun create(real: ConsoleCommandSender, session: EchoSession, sourceId: String): ConsoleCommandSender? {
        return runCatching {
            Proxy.newProxyInstance(
                ConsoleCommandSender::class.java.classLoader,
                arrayOf(ConsoleCommandSender::class.java),
                Handler(real, session, sourceId)
            ) as ConsoleCommandSender
        }.getOrNull()
    }

    private class Handler(
        private val real: ConsoleCommandSender,
        private val session: EchoSession,
        private val sourceId: String
    ) : InvocationHandler {

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
            @Suppress("UNCHECKED_CAST")
            val arguments = (args ?: emptyArray<Any?>()) as Array<Any?>
            return when (method.name) {
                "sendMessage", "sendRawMessage", "sendRichMessage", "acceptConversationInput" -> {
                    arguments.forEach { value ->
                        if (method.name == "sendRichMessage" && value is String) captureRich(value) else capture(value)
                    }
                    null
                }
                "spigot" -> SpigotEchoAdapter(real, session, sourceId)
                "equals" -> proxy === arguments.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlumBotEchoProxy(" + real.name + ")"
                else -> try {
                    method.invoke(real, *arguments)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        }

        private fun capture(value: Any?) {
            when (value) {
                null -> Unit
                is Component -> session.component(sourceId, value)
                is ComponentLike -> session.component(sourceId, value.asComponent())
                is String -> session.line(sourceId, value)
                is Array<*> -> value.forEach(::capture)
                else -> {
                    // net.md_5.bungee.api.chat.BaseComponent 等宿主的 legacy 组件：
                    // 不编译期依赖 bungee-chat，直接反射 toLegacyText()。
                    val legacy = runCatching {
                        value.javaClass.getMethod("toLegacyText").invoke(value) as? String
                    }.getOrNull()
                    if (legacy != null) session.line(sourceId, legacy)
                }
            }
        }

        /**
         * `CommandSender.Spigot` 是**类**而不是接口，无法用 `Proxy` 拦截，只能子类化。
         * 插件常用 `sender.spigot().sendMessage(BaseComponent)`，不覆写就会绕过回显会话；
         * 捕获后仍转发给真实 console，保持服务端日志行为。
         */
        private class SpigotEchoAdapter(
            private val real: ConsoleCommandSender,
            private val session: EchoSession,
            private val sourceId: String
        ) : CommandSender.Spigot() {

            override fun sendMessage(component: BaseComponent) {
                capture(component)
                forward { it.sendMessage(component) }
            }

            override fun sendMessage(vararg components: BaseComponent) {
                components.forEach(::capture)
                forward { it.sendMessage(*components) }
            }

            private fun capture(component: BaseComponent) {
                session.line(sourceId, component.toLegacyText())
            }

            private fun forward(action: (CommandSender.Spigot) -> Unit) {
                runCatching { action(real.spigot()) }
            }
        }

        private fun captureRich(text: String) {
            val component = runCatching {
                val miniMessage = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage")
                val instance = miniMessage.getMethod("miniMessage").invoke(null)
                miniMessage.getMethod("deserialize", String::class.java).invoke(instance, text) as? Component
            }.getOrNull()
            if (component != null) {
                session.component(sourceId, component)
            } else {
                session.line(sourceId, text)
            }
        }
    }
}