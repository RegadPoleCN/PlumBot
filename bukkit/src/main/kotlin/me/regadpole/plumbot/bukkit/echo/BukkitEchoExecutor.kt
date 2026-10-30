// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformLogger
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.internal.RemoteConsoleAudit
import me.regadpole.plumbot.utils.ComponentProjector
import me.regadpole.plumbot.utils.MinecraftTranslationSource
import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Bukkit/Paper 的多通道远程回显执行器。
 *
 * 线程模型：[submit] 可在任意线程调用（主线程直接执行，异步线程经 scheduler.runSync 调度）；
 * 通道探测、日志源 attach、指令派发都在主线程（executeInline）完成；派发后绝不在主线程
 * sleep，由守护调度线程在 capture_window 后 detach 并组装结果，避免 400ms 主线程冻结。
 */
internal class BukkitEchoExecutor(
    private val settingsProvider: () -> EchoSettings,
    private val logger: BukkitPlatformLogger,
    private val scheduler: PlatformScheduler
) {
    private val debug: (String) -> Unit = { message -> logger.log(LogLevel.DEBUG, "[RemoteEcho] " + message) }
    private val paperFeedback = PaperFeedbackEcho(debug)
    private val nms = NmsEcho(debug)
    private var nmsUnavailableWarned = false
    @Volatile
    private var lastResult: EchoResult? = null

    /** 预热只做一次（onEnable 调度，reload 不重复）。 */
    private val warmUpStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** 最近一次**主线程**上花在派发链路的耗时，只用于诊断展示。 */
    private class MainThreadTiming(val prepareNanos: Long, val dispatchNanos: Long)

    @Volatile
    private var lastMainTiming: MainThreadTiming? = null

    private val finalizer = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "PlumBot-Echo-Finalizer").apply { isDaemon = true }
    }
    private val rconExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlumBot-Echo-Rcon").apply { isDaemon = true }
    }

    /**
     * 预热回显链路：把首次派发才做的类加载/反射探测提前到启动期异步线程。冷启动第一次
     * 远程指令实测让主线程单 tick 花掉 72ms（`PaperFeedbackEcho.prepare` 16ms、`dispatch`
     * 48ms 里的 `ComponentProjector`/en_us 首次解析、类加载 4ms）。幂等：重复调用只生效一次。
     */
    fun warmUp() {
        if (!warmUpStarted.compareAndSet(false, true)) return
        try {
            paperFeedback.prewarm()
            ComponentProjector.warmUp()
            MinecraftTranslationSource.warmUp()
            Log4j2EchoSource.prewarm(debug)
            runCatching { settingsProvider() }.getOrNull()?.let { EchoChannelSelector.candidates(it) }
        } catch (t: Throwable) {
            debug("回显预热失败: " + (t.message ?: t.javaClass.simpleName))
        }
    }

    fun submit(command: String): CompletableFuture<EchoResult> {
        val future = CompletableFuture<EchoResult>()
        future.whenComplete { result, _ -> if (result != null) lastResult = result }
        val normalized = command.trim().removePrefix("/").trim()
        if (normalized.isEmpty()) {
            future.complete(
                EchoResult(
                    state = EchoState.FAILED,
                    channel = "none",
                    code = EchoCode.ECHO_DISPATCH_FALSE,
                    detail = "指令为空"
                )
            )
            return future
        }
        val settings = try {
            settingsProvider()
        } catch (t: Throwable) {
            future.complete(
                EchoResult(
                    state = EchoState.FAILED,
                    channel = "none",
                    code = EchoCode.ECHO_EXCEPTION,
                    detail = t.message ?: t.javaClass.simpleName
                )
            )
            return future
        }
        val task: () -> Unit = {
            try {
                executeInline(future, normalized, settings)
            } catch (t: Throwable) {
                completeFailure(future, settings, t)
            }
        }
        return try {
            if (Bukkit.isPrimaryThread()) {
                task()
                future
            } else {
                scheduler.runSync(task)
                awaitDispatch(future, settings)
            }
        } catch (t: Throwable) {
            completeFailure(future, settings, t)
            future
        }
    }

    /**
     * 供 `/plumbot doctor echo` 使用：只做只读探测，不派发指令、不改配置。输出不含 RCON 密码；
     * 最近一次结果只保留状态、通道、错误码、耗时与行数，避免把敏感控制台正文带进聊天窗口。
     */
    fun diagnostics(): List<String> {
        val settings = runCatching { settingsProvider() }.getOrElse { error ->
            return listOf("配置读取失败: " + (error.message ?: error.javaClass.simpleName))
        }
        val lines = ArrayList<String>(12)
        val candidates = EchoChannelSelector.candidates(settings)
        lines += "候选通道: " + candidates.joinToString(" -> ").ifBlank { "（未配置）" }
        if (settings.forced.isNotEmpty()) {
            lines += "强制通道: " + settings.forced.joinToString(", ") + "（失败不会自动换通道）"
        }
        lines += "paper_feedback: " + availability(paperFeedback.available, paperFeedback.unavailableReason)
        lines += "plugin_command: 可用（按 label 查找插件命令；找不到则换下一通道）"
        lines += "console: 可用（真实控制台派发 + ConsoleCommandSender 代理兜底）"
        lines += "log4j2: " + (if (settings.log4j2) "已启用（派发时 attach）" else "已禁用")
        lines += "JUL: " + (if (settings.jul) "已启用（派发时 attach）" else "已禁用")
        val latestLog = java.io.File("logs/latest.log")
        lines += "latest.log: " + when {
            !settings.latestLog -> "已禁用"
            latestLog.isFile && latestLog.canRead() -> "可读（" + latestLog.absolutePath + "）"
            else -> "不可读（" + latestLog.absolutePath + "）"
        }
        lines += "rcon: " + if (settings.rcon.enabled) {
            "已启用（" + settings.rcon.host + ":" + settings.rcon.port + "，超时 " + settings.rcon.timeoutMs + "ms）"
        } else "已禁用"
        lines += "nms: " + if (settings.nmsEnabled) availability(nms.available, nms.unavailableReason) else "已禁用"
        lines += "minecraft_translations: " + MinecraftTranslationSource.status()
        lines += "proxy_sender: " + if (settings.proxySenderEnabled) "已启用（实验通道，Paper 可能仍丢 sender）" else "已禁用"
        lines += "线程模型: " + (if (Bukkit.isPrimaryThread()) "当前在主线程" else "当前在异步线程") +
            "；派发超时 " + settings.dispatchTimeoutMs + "ms，捕获窗口 " + settings.captureWindowMs +
            "ms，调用方等待上限 " + echoWaitBudgetMs(settings) + "ms"
        val last = lastResult
        lines += if (last == null) {
            "最近一次: 本会话尚未执行远程指令"
        } else {
            "最近一次: state=" + last.state + " channel=" + last.channel +
                (last.code?.let { " code=" + it } ?: "") +
                " duration=" + last.durationMs + "ms lines=" + last.lines.size +
                (lastMainTiming?.let { timing ->
                    " main=" + millisText(timing.prepareNanos + timing.dispatchNanos) +
                        "(prepare=" + millisText(timing.prepareNanos) +
                        " dispatch=" + millisText(timing.dispatchNanos) + ")"
                } ?: "") +
                (last.detail?.takeIf { it.isNotBlank() }?.let { " detail=" + it } ?: "")
        }
        return lines
    }

    /** 纳秒转带一位小数的毫秒文本（避免为格式化引入 Locale）。 */
    private fun millisText(nanos: Long): String = ((nanos / 100_000L).toDouble() / 10.0).toString() + "ms"

    private fun availability(available: Boolean, reason: String): String =
        if (available) "可用" else "不可用（" + reason + "）"

    private fun completeFailure(future: CompletableFuture<EchoResult>, settings: EchoSettings, error: Throwable) {
        if (future.isDone) return
        future.complete(
            EchoResult(
                state = EchoState.FAILED,
                channel = "none",
                durationMs = settings.dispatchTimeoutMs,
                code = EchoCode.ECHO_EXCEPTION,
                detail = error.message ?: error.javaClass.simpleName
            )
        )
    }

    private fun awaitDispatch(future: CompletableFuture<EchoResult>, settings: EchoSettings): CompletableFuture<EchoResult> {
        val waitMs = echoWaitBudgetMs(settings)
        try {
            future.get(waitMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.complete(
                EchoResult(
                    state = EchoState.FAILED,
                    channel = "none",
                    durationMs = waitMs,
                    code = EchoCode.ECHO_TIMEOUT,
                    detail = "等待派发与捕获窗口超时（" + waitMs + "ms）；指令可能已提交，结果未知"
                )
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            completeFailure(future, settings, e)
        } catch (e: ExecutionException) {
            completeFailure(future, settings, e.cause ?: e)
        }
        return future
    }

    /** 只在主线程调用：审计、选路、attach、派发。 */
    private fun executeInline(future: CompletableFuture<EchoResult>, command: String, settings: EchoSettings) {
        // 即使调用方在排队期间已超时（future 已完成），也继续派发：主线程调度延迟时
        // 指令可能尚未提交，直接丢弃会让“超时=结果未知”的语义变成“静默不执行”。
        // finalize 会看到 future 已完成，只做日志源 detach，不再重复 complete。
        val session = EchoSession(System.currentTimeMillis(), settings.includeLogPrefix)
        logger.log(LogLevel.WARN, RemoteConsoleAudit.describe(command, settings.logCommands))

        val reasons = ArrayList<String>()
        val candidates = EchoChannelSelector.candidates(settings)
        val prepareStartNanos = System.nanoTime()
        for (candidate in candidates) {
            val prepared = prepare(candidate, command, session, settings, reasons) ?: continue
            val prepareDoneNanos = System.nanoTime()
            dispatchPrepared(prepared, command, session, settings, future)
            // 把"主线程税"分段记下来，doctor echo 直接看得到，不必再抓 spark。
            lastMainTiming = MainThreadTiming(
                prepareNanos = prepareDoneNanos - prepareStartNanos,
                dispatchNanos = System.nanoTime() - prepareDoneNanos
            )
            return
        }

        session.close()
        val forced = settings.forced.isNotEmpty()
        future.complete(
            EchoResult(
                state = EchoState.FAILED,
                channel = candidates.firstOrNull() ?: "none",
                code = if (forced) EchoCode.ECHO_CHANNEL_UNAVAILABLE else EchoCode.ECHO_NO_CHANNEL,
                detail = reasons.joinToString("；").ifBlank {
                    if (forced) "指定的回显通道不可用" else "没有可用的回显通道"
                }.take(500)
            )
        )
    }

    private fun prepare(
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

    private fun dispatchPrepared(
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
                                durationMs = elapsedMillis(startNanos),
                                detail = prepared.detail
                            )
                        )
                    }
                    return
                }
                val delay = captureDelay(settings, startNanos)
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
                    durationMs = elapsedMillis(startNanos),
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
                future.complete(rconResult(prepared.channel, outcome, settings, elapsedMillis(startNanos)))
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

    private fun rconResult(
        channel: String,
        outcome: RconOutcome,
        settings: EchoSettings,
        durationMs: Long
    ): EchoResult {
        return when (outcome) {
            is RconOutcome.AuthFailed -> EchoResult(
                state = EchoState.FAILED,
                channel = channel,
                durationMs = durationMs,
                code = EchoCode.ECHO_RCON_AUTH_FAILED,
                detail = outcome.detail
            )
            is RconOutcome.Error -> EchoResult(
                state = EchoState.FAILED,
                channel = channel,
                durationMs = durationMs,
                code = if (outcome.detail.contains("超时", ignoreCase = true) ||
                    outcome.detail.contains("timeout", ignoreCase = true)
                ) EchoCode.ECHO_TIMEOUT else EchoCode.ECHO_EXCEPTION,
                detail = outcome.detail
            )
            is RconOutcome.Success -> {
                val lines = outcome.lines
                    .map { ComponentProjector.ansiToSection(it).replace("\r", "").trim() }
                    .filter { it.isNotEmpty() }
                val matched = settings.failurePatterns.firstOrNull { pattern ->
                    pattern.isNotBlank() && lines.any { it.contains(pattern, ignoreCase = true) }
                }
                when {
                    matched != null -> EchoResult(
                        state = EchoState.UNKNOWN_OR_REJECTED,
                        channel = channel,
                        lines = lines,
                        durationMs = durationMs,
                        code = EchoCode.ECHO_UNKNOWN_COMMAND,
                        detail = "回显命中失败模式: " + matched.take(80)
                    )
                    lines.isNotEmpty() -> EchoResult(
                        state = EchoState.CAPTURED,
                        channel = channel,
                        lines = lines,
                        durationMs = durationMs
                    )
                    else -> EchoResult(
                        state = EchoState.SUCCESS_NO_OUTPUT,
                        channel = channel,
                        durationMs = durationMs,
                        code = EchoCode.ECHO_NO_CHANNEL,
                        detail = "RCON 已执行，但没有响应内容"
                    )
                }
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

    private fun captureDelay(settings: EchoSettings, startNanos: Long): Long {
        val remaining = (settings.maxCaptureMs - elapsedMillis(startNanos)).coerceAtLeast(0L)
        return minOf(settings.captureWindowMs, remaining)
    }

    private fun elapsedMillis(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * 调用方可等待的总时长：主线程派发上限 + 捕获窗口。结果要等捕获窗口关闭才 complete，
 * 只等 `dispatch_timeout_ms` 的话，一旦 `capture_window_ms` 超过它，每次调用都先报
 * ECHO_TIMEOUT（指令其实已执行、回显也已拿到，只是被丢弃）。窗口受 `max_capture_ms` 约束。
 */
internal fun echoWaitBudgetMs(settings: EchoSettings): Long =
    settings.dispatchTimeoutMs + minOf(settings.captureWindowMs, settings.maxCaptureMs)