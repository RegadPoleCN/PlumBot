// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.bukkit.echo.config.EchoSettings

internal object EchoTiming {

    internal fun captureDelay(settings: EchoSettings, startNanos: Long): Long {
        val remaining = (settings.maxCaptureMs - elapsedMillis(startNanos)).coerceAtLeast(0L)
        return minOf(settings.captureWindowMs, remaining)
    }

    internal fun elapsedMillis(startNanos: Long): Long =
        ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)
}

/**
 * 调用方可等待的总时长：主线程派发上限 + 捕获窗口。结果要等捕获窗口关闭才 complete，
 * 只等 `dispatch_timeout_ms` 的话，一旦 `capture_window_ms` 超过它，每次调用都先报
 * ECHO_TIMEOUT（指令其实已执行、回显也已拿到，只是被丢弃）。窗口受 `max_capture_ms` 约束。
 */
internal fun echoWaitBudgetMs(settings: EchoSettings): Long =
    settings.dispatchTimeoutMs + minOf(settings.captureWindowMs, settings.maxCaptureMs)
