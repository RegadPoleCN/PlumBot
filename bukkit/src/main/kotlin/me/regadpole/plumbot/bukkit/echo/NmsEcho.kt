// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.bukkit.echo.capture.EchoSession
import me.regadpole.plumbot.bukkit.echo.config.EchoChannels
import me.regadpole.plumbot.bukkit.echo.logsource.EchoSource
import me.regadpole.plumbot.bukkit.echo.support.EchoReflection
import org.bukkit.Bukkit
import java.lang.reflect.InvocationTargetException

/**
 * NMS Brigadier 通道（默认关闭，仅作为排障开关）。直接走 MinecraftServer 的 CommandDispatcher，
 * 绕过 Bukkit 对 ConsoleCommandSender 的所有身份替换，代价是跨版本反射路径不稳定；输出仍由
 * 日志旁路捕获，这里只负责派发与识别信号。
 */
internal class NmsEcho(private val debug: (String) -> Unit) {

    private data class Probe(val dispatcher: Any, val source: Any)

    private val lock = Any()
    private var probed = false
    private var probe: Probe? = null
    private var reason: String? = null

    val available: Boolean
        get() = probe() != null

    val unavailableReason: String
        get() {
            probe()
            return reason ?: "NMS Brigadier 通道不可用"
        }

    fun prepare(session: EchoSession, sources: List<EchoSource>): PreparedLocalEcho? {
        val current = probe() ?: return null
        return PreparedLocalEcho(
            channel = EchoChannels.NMS,
            dispatcher = { command -> dispatch(current, command) },
            finishAction = { finishedSession -> sources.forEach { runCatching { it.finish(finishedSession) } } },
            detail = "NMS Brigadier"
        )
    }

    private fun dispatch(current: Probe, command: String): Boolean {
        val parse = parseMethod(current.dispatcher)
            ?: throw IllegalStateException("NMS CommandDispatcher 缺少 parse(String, source)")
        val execute = executeMethod(current.dispatcher)
            ?: throw IllegalStateException("NMS CommandDispatcher 缺少 execute(ParseResults)")
        val parsed = try {
            parse.invoke(current.dispatcher, command, current.source)
        } catch (e: InvocationTargetException) {
            if (isSyntaxError(e.targetException)) return false
            throw e.targetException
        }
        val result = try {
            execute.invoke(current.dispatcher, parsed)
        } catch (e: InvocationTargetException) {
            if (isSyntaxError(e.targetException)) return false
            throw e.targetException
        }
        return (result as? Number)?.toInt()?.let { it > 0 } ?: true
    }

    private fun parseMethod(dispatcher: Any) =
        EchoReflection.method(dispatcher.javaClass, "parse", 2)
            ?.takeIf { it.parameterTypes[0] == String::class.java }

    private fun executeMethod(dispatcher: Any) =
        EchoReflection.method(dispatcher.javaClass, "execute", 1)

    private fun isSyntaxError(error: Throwable): Boolean =
        error.javaClass.name.contains("CommandSyntaxException") ||
            (error.message?.contains("Unknown or incomplete command", ignoreCase = true) == true) ||
            (error.message?.contains("Unknown command", ignoreCase = true) == true)

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
        val minecraftServer = EchoReflection.method(server.javaClass, "getServer", 0)?.invoke(server)
            ?: run {
                reason = "CraftServer.getServer() 不可用"
                return null
            }
        val commandDispatcher = runCatching {
            EchoReflection.method(minecraftServer.javaClass, "getCommands", 0)?.invoke(minecraftServer)
                ?: EchoReflection.method(minecraftServer.javaClass, "getCommandDispatcher", 0)?.invoke(minecraftServer)
        }.getOrNull() ?: run {
            reason = "MinecraftServer.getCommands()/getCommandDispatcher() 不可用"
            return null
        }
        val dispatcher = EchoReflection.method(commandDispatcher.javaClass, "getDispatcher", 0)
            ?.invoke(commandDispatcher) ?: commandDispatcher
        val source = runCatching {
            EchoReflection.method(minecraftServer.javaClass, "createCommandSourceStack", 0)?.invoke(minecraftServer)
        }.getOrNull() ?: run {
            reason = "MinecraftServer.createCommandSourceStack() 不可用"
            return null
        }
        if (parseMethod(dispatcher) == null || executeMethod(dispatcher) == null) {
            reason = "CommandDispatcher 缺少 parse/execute 方法"
            return null
        }
        return Probe(dispatcher, source)
    }
}
