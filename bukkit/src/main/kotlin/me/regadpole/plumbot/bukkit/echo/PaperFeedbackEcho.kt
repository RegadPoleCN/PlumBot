// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.function.Consumer

/**
 * Paper 的 FeedbackForwardingSender 通道。Paper 的 CraftServer.dispatchCommand 对
 * ConsoleCommandSender 会重建 CommandSourceStack，普通代理因此收不到反馈；Paper 为此提供
 * createCommandSender(Consumer<Component>) / FeedbackForwardingSender，绕过 Bukkit 的 sender
 * 身份判断，直接由 Paper 把指令反馈转给我们的 Consumer。该 API 在 Spigot 上不存在，所以全部
 * 反射探测；失败只降级，不抛异常、不 WARN。
 */
internal class PaperFeedbackEcho(private val debug: (String) -> Unit) {

    private data class Probe(
        val createMethod: Method?,
        val feedbackConstructor: Constructor<*>?
    )

    private val lock = Any()
    private var probed = false
    private var probe: Probe? = null
    private var reason: String? = null

    /**
     * 复用的反馈发送器。每次派发都新建会重复反射构造 + 触发一次类加载（实测 4ms 落在
     * `ClassLoader.loadClass`）；sender 只与"把反馈交给谁"有关，所以创建一次、用
     * [activeSession] 指向当前会话。
     */
    @Volatile
    private var cachedSender: CommandSender? = null

    /** 当前正在接收反馈的会话；sender 的 Consumer 从这里取目标。 */
    @Volatile
    private var activeSession: EchoSession? = null

    val available: Boolean
        get() = probe() != null

    /**
     * 把通道探测与 sender 构造提前到启动期异步线程：冷启动时这一步 + Paper 侧 Brigadier
     * 首次执行曾让主线程单 tick 花掉 72ms。
     */
    fun prewarm() {
        val current = probe() ?: return
        sender(current)
    }

    val unavailableReason: String
        get() {
            probe()
            return reason ?: "Paper 反馈通道不可用"
        }

    fun prepare(session: EchoSession): PreparedLocalEcho? {
        val current = probe() ?: return null
        val sender = sender(current) ?: return null
        // 反馈是**同步**回调（Bukkit.dispatchCommand 期间），因此 activeSession 的
        // 生命周期严格包住一次 dispatch；finishAction 兜底清理，避免下次误投。
        activeSession = session
        return PreparedLocalEcho(
            channel = EchoChannels.PAPER_FEEDBACK,
            dispatcher = { command -> Bukkit.dispatchCommand(sender, command) },
            finishAction = { activeSession = null },
            detail = null
        )
    }

    /** 取（或首次创建并缓存）反馈发送器。 */
    private fun sender(current: Probe): CommandSender? {
        cachedSender?.let { return it }
        val created = createSender(current) ?: return null
        cachedSender = created
        return created
    }

    private fun createSender(current: Probe): CommandSender? {
        val consumer = Consumer<Any?> { value ->
            activeSession?.let { capture(it, value) }
        }
        current.createMethod?.let { method ->
            runCatching { method.invoke(Bukkit.getServer(), consumer) as? CommandSender }
                .getOrNull()
                ?.let { return it }
        }
        current.feedbackConstructor?.let { constructor ->
            runCatching { constructor.newInstance(consumer, Bukkit.getServer()) as? CommandSender }
                .getOrNull()
                ?.let { return it }
        }
        synchronized(lock) { reason = "Paper createCommandSender/FeedbackForwardingSender 调用失败" }
        debug("Paper 反馈发送器创建失败")
        return null
    }

    private fun capture(session: EchoSession, value: Any?) {
        when (value) {
            null -> Unit
            is Component -> session.component(EchoChannels.PAPER_FEEDBACK, value)
            is ComponentLike -> session.component(EchoChannels.PAPER_FEEDBACK, value.asComponent())
            is String -> session.line(EchoChannels.PAPER_FEEDBACK, value)
            else -> {
                val legacy = runCatching {
                    value.javaClass.getMethod("toLegacyText").invoke(value) as? String
                }.getOrNull()
                if (legacy != null) session.line(EchoChannels.PAPER_FEEDBACK, legacy)
            }
        }
    }

    private fun probe(): Probe? {
        if (probed) return probe
        synchronized(lock) {
            if (probed) return probe
            probe = runCatching { discover() }.getOrElse {
                reason = it.message ?: it.javaClass.simpleName
                null
            }
            probed = true
            return probe
        }
    }

    private fun discover(): Probe? {
        val server = Bukkit.getServer() ?: return null
        val createMethod = server.javaClass.methods.firstOrNull { candidate ->
            candidate.name == "createCommandSender" &&
                candidate.parameterCount == 1 &&
                Consumer::class.java.isAssignableFrom(candidate.parameterTypes[0])
        }
        val feedbackConstructor = runCatching {
            val clazz = Class.forName("io.papermc.paper.commands.FeedbackForwardingSender")
            clazz.constructors.firstOrNull { candidate ->
                candidate.parameterCount == 2 &&
                    Consumer::class.java.isAssignableFrom(candidate.parameterTypes[0]) &&
                    candidate.parameterTypes[1].isInstance(server)
            }
        }.getOrNull()
        if (createMethod == null && feedbackConstructor == null) {
            reason = "当前服务端没有 Paper FeedbackForwardingSender"
            debug(reason!!)
            return null
        }
        return Probe(createMethod, feedbackConstructor)
    }
}
