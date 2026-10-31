// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.bukkit.echo.capture.EchoResultBuilder
import me.regadpole.plumbot.bukkit.echo.capture.EchoSession
import me.regadpole.plumbot.bukkit.echo.capture.EchoTiming
import me.regadpole.plumbot.bukkit.echo.config.EchoChannels
import me.regadpole.plumbot.bukkit.echo.config.EchoSettings
import me.regadpole.plumbot.bukkit.echo.logsource.EchoLogSources
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformLogger
import me.regadpole.plumbot.internal.LogLevel
import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

internal class EchoDispatcher(
    private val paperFeedback: PaperFeedbackEcho,
    private val nms: NmsEcho,
    private val logger: BukkitPlatformLogger,
    private val debug: (String) -> Unit
) {

    private var nmsUnavailableWarned = false

    private val finalizer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PlumBot-Echo-Finalizer").apply { isDaemon = true }
    }
    private val rconExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlumBot-Echo-Rcon").apply { isDaemon = true }
    }

    internal fun prepare(
        channel: String,
        command: String,
        session: EchoSession,
        settings: EchoSettings,
        reasons: MutableList<String>
    ): PreparedEcho? {
        return when (channel) {
            EchoChannels.PAPER_FEEDBACK -> {
                if (!settings.paperFeedback) {
                    reasons += "paper_feedback 已禁用"
                    null
                } else {
                    val prepared = paperFeedback.prepare(session)
                    if (prepared == null) reasons += "paper_feedback: " + paperFeedback.unavailableReason
                    prepared
                }
            }
            EchoChannels.PLUGIN_COMMAND -> preparePluginCommand(command, session, reasons)
            EchoChannels.CONSOLE -> prepareConsole(session, settings)
            EchoChannels.RCON -> {
                if (!settings.rcon.enabled) {
                    reasons += "rcon 未启用"
                    null
                } else {
                    PreparedRconEcho(
                        EchoChannels.RCON,
                        RconEchoClient(
                            settings.rcon.host,
                            settings.rcon.port,
                            settings.rcon.password,
                            settings.rcon.timeoutMs
                        )
                    )
                }
            }
            EchoChannels.NMS -> {
                if (!settings.nmsEnabled) {
                    reasons += "nms 未启用"
                    null
                } else {
                    val sources = EchoLogSources.create(session, settings, debug)
                    val prepared = nms.prepare(session, sources)
                    if (prepared == null) {
                        sources.forEach { runCatching { it.finish(session) } }
                        reasons += "nms: " + nms.unavailableReason
                        if (!nmsUnavailableWarned) {
                            nmsUnavailableWarned = true
                            logger.log(LogLevel.WARN, "[RemoteEcho] nms 通道不可用: " + nms.unavailableReason)
                        }
                    }
                    prepared
                }
            }
            EchoChannels.PROXY_SENDER -> {
                if (!settings.proxySenderEnabled) {
                    reasons += "proxy_sender 未启用"
                    null
                } else {
                    val proxy = BukkitEchoProxyFactory.create(
                        Bukkit.getConsoleSender(),
                        session,
                        EchoChannels.PROXY_SENDER
                    )
                    if (proxy == null) {
                        reasons += "proxy_sender: 无法创建 ConsoleCommandSender 代理"
                        null
                    } else {
                        PreparedLocalEcho(
                            EchoChannels.PROXY_SENDER,
                            { cmd -> Bukkit.dispatchCommand(proxy, cmd) },
                            detail = "Bukkit.dispatchCommand(proxy)；Paper 上派发路径可能丢弃 sender"
                        )
                    }
                }
            }
            else -> {
                reasons += "未知通道: " + channel
                null
            }
        }
    }

    private fun preparePluginCommand(
        command: String,
        session: EchoSession,
        reasons: MutableList<String>
    ): PreparedLocalEcho? {
        val parts = command.split(WHITESPACE, limit = 2)
        val label = parts.firstOrNull()?.takeIf { it.isNotBlank() }
        if (label == null) {
            reasons += "plugin_command: 指令为空"
            return null
        }
        val pluginCommand = Bukkit.getPluginCommand(label)
        if (pluginCommand == null) {
            reasons += "plugin_command: " + label + " 不是已注册的插件指令"
            return null
        }
        val proxy = BukkitEchoProxyFactory.create(
            Bukkit.getConsoleSender(),
            session,
            EchoChannels.PLUGIN_COMMAND
        )
        if (proxy == null) {
            reasons += "plugin_command: 无法创建 ConsoleCommandSender 代理"
            return null
        }
        return PreparedLocalEcho(
            channel = EchoChannels.PLUGIN_COMMAND,
            dispatcher = { raw ->
                val split = raw.split(WHITESPACE, limit = 2)
                val args = if (split.size > 1) {
                    split[1].split(WHITESPACE).filter { it.isNotEmpty() }.toTypedArray()
                } else {
                    emptyArray()
                }
                pluginCommand.execute(proxy, split.firstOrNull() ?: label, args)
            },
            detail = "PluginCommand.execute(proxy, label, args)"
        )
    }

    private fun prepareConsole(session: EchoSession, settings: EchoSettings): PreparedLocalEcho {
        val real: ConsoleCommandSender = Bukkit.getConsoleSender()
        val sender = BukkitEchoProxyFactory.create(real, session, EchoChannels.CONSOLE) ?: real
        val sources = EchoLogSources.create(session, settings, debug)
        return PreparedLocalEcho(
            channel = EchoChannels.CONSOLE,
            dispatcher = { cmd -> Bukkit.dispatchCommand(sender, cmd) },
            finishAction = { finishedSession ->
                sources.forEach { runCatching { it.finish(finishedSession) } }
            },
            detail = "真实控制台派发 + log4j2/JUL/latest.log 旁路"
        )
    }

    internal fun dispatchPrepared(
        prepared: PreparedEcho,
        command: String,
        session: EchoSession,
        settings: EchoSettings,
        future: CompletableFuture<EchoResult>
    ) {
        val startNanos = System.nanoTime()
        when (prepared) {
            is PreparedRconEcho -> submitRcon(prepared, command, settings, startNanos, future)
            is PreparedLocalEcho -> {
                var dispatchOk = false
                var dispatchError: Throwable? = null
                try {
                    dispatchOk = prepared.dispatch(command)
                } catch (t: Throwable) {
                    dispatchError = t
                }
                if (dispatchError != null) {
                    finishQuietly(prepared, session, dispatchError)
                    session.close()
                    if (!future.isDone) {
                        future.complete(
                            EchoResultBuilder.build(
                                channel = effectiveChannel(prepared, session),
                                lines = session.snapshot(),
                                dispatchOk = false,
                                exception = dispatchError,
                                failurePatterns = settings.failurePatterns,
                                durationMs = EchoTiming.elapsedMillis(startNanos),
                                detail = prepared.detail
                            )
                        )
                    }
                    return
                }
                val delay = EchoTiming.captureDelay(settings, startNanos)
                val finalizeTask = {
                    finalizeLocal(prepared, session, dispatchOk, settings, startNanos, future)
                }
                try {
                    finalizer.schedule(finalizeTask, delay, TimeUnit.MILLISECONDS)
                } catch (_: RejectedExecutionException) {
                    finalizeTask()
                }
            }
            else -> {
                session.close()
                if (!future.isDone) {
                    future.complete(
                        EchoResult(
                            state = EchoState.FAILED,
                            channel = prepared.channel,
                            code = EchoCode.ECHO_CHANNEL_UNAVAILABLE,
                            detail = "无法识别的通道类型"
                        )
                    )
                }
            }
        }
    }

    private fun finalizeLocal(
        prepared: PreparedLocalEcho,
        session: EchoSession,
        dispatchOk: Boolean,
        settings: EchoSettings,
        startNanos: Long,
        future: CompletableFuture<EchoResult>
    ) {
        // 即使调用方已超时（future 已完成），也必须 detach 日志源，否则 appender/handler
        // 会一直挂在 root logger 上。先收尾再判断是否还需要组装结果。
        runCatching { prepared.finish(session) }.onFailure { debug("回显通道 detach 失败: " + it.message) }
        session.close()
        if (future.isDone) return
        val lines = session.snapshot()
        if (!future.isDone) {
            future.complete(
                EchoResultBuilder.build(
                    channel = effectiveChannel(prepared, session),
                    lines = lines,
                    dispatchOk = dispatchOk,
                    exception = null,
                    failurePatterns = settings.failurePatterns,
                    durationMs = EchoTiming.elapsedMillis(startNanos),
                    detail = prepared.detail
                )
            )
        }
    }

    private fun submitRcon(
        prepared: PreparedRconEcho,
        command: String,
        settings: EchoSettings,
        startNanos: Long,
        future: CompletableFuture<EchoResult>
    ) {
        val task = {
            val outcome = try {
                prepared.execute(command)
            } catch (t: Throwable) {
                RconOutcome.Error(t.message ?: t.javaClass.simpleName)
            }
            if (!future.isDone) {
                future.complete(RconResultBuilder.build(prepared.channel, outcome, settings, EchoTiming.elapsedMillis(startNanos)))
            }
        }
        try {
            rconExecutor.execute(task)
        } catch (t: Throwable) {
            if (!future.isDone) {
                future.complete(
                    EchoResult(
                        state = EchoState.FAILED,
                        channel = prepared.channel,
                        code = EchoCode.ECHO_EXCEPTION,
                        detail = t.message ?: t.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun finishQuietly(prepared: PreparedLocalEcho, session: EchoSession, error: Throwable) {
        runCatching { prepared.finish(session) }
            .onFailure { debug("异常路径 detach 失败: " + (it.message ?: error.message)) }
    }

    /** console 通道可能由日志旁路补获：把 channel 细化成真正贡献首行的来源，便于判断哪条旁路生效。 */
    private fun effectiveChannel(prepared: PreparedLocalEcho, session: EchoSession): String {
        if (prepared.channel != EchoChannels.CONSOLE) return prepared.channel
        return when (val first = session.sources().firstOrNull()) {
            "log4j2", "jul", "latest_log" -> first
            else -> EchoChannels.CONSOLE
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
