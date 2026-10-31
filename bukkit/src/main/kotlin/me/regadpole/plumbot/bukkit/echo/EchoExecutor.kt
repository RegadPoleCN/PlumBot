// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.bukkit.echo.capture.EchoSession
import me.regadpole.plumbot.bukkit.echo.capture.echoWaitBudgetMs
import me.regadpole.plumbot.bukkit.echo.config.EchoSettings
import me.regadpole.plumbot.bukkit.echo.logsource.Log4j2EchoSource
import me.regadpole.plumbot.bukkit.platform.BukkitPlatformLogger
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.internal.RemoteConsoleAudit
import me.regadpole.plumbot.utils.ComponentProjector
import me.regadpole.plumbot.utils.MinecraftTranslationSource
import org.bukkit.Bukkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Bukkit/Paper 的多通道远程回显执行器。
 *
 * 线程模型：[submit] 可在任意线程调用（主线程直接执行，异步线程经 scheduler.runSync 调度）；
 * 通道探测、日志源 attach、指令派发都在主线程（executeInline）完成；派发后绝不在主线程
 * sleep，由守护调度线程在 capture_window 后 detach 并组装结果，避免 400ms 主线程冻结。
 */
internal class EchoExecutor(
    private val settingsProvider: () -> EchoSettings,
    private val logger: BukkitPlatformLogger,
    private val scheduler: PlatformScheduler
) {
    private val debug: (String) -> Unit = { message -> logger.log(LogLevel.DEBUG, "[RemoteEcho] " + message) }
    private val paperFeedback = PaperFeedbackEcho(debug)
    private val nms = NmsEcho(debug)
    private val dispatcher = EchoDispatcher(paperFeedback, nms, logger, debug)

    @Volatile
    private var lastResult: EchoResult? = null

    /** 预热只做一次（onEnable 调度，reload 不重复）。 */
    private val warmUpStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var lastMainTiming: MainThreadTiming? = null

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
        return EchoDiagnostics.render(settings, paperFeedback, nms, lastResult, lastMainTiming)
    }

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
            val prepared = dispatcher.prepare(candidate, command, session, settings, reasons) ?: continue
            val prepareDoneNanos = System.nanoTime()
            dispatcher.dispatchPrepared(prepared, command, session, settings, future)
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
}
