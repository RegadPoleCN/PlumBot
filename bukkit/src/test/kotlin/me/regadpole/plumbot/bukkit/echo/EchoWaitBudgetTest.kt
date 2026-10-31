// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.bukkit.echo.capture.echoWaitBudgetMs
import me.regadpole.plumbot.bukkit.echo.config.EchoChannels
import me.regadpole.plumbot.bukkit.echo.config.EchoSettings
import me.regadpole.plumbot.bukkit.echo.config.RconSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [echoWaitBudgetMs] 契约测试。
 *
 * 回归背景：结果要等 finalizer 延迟 capture 窗口后才 complete，而 awaitDispatch 原先只等
 * dispatch_timeout_ms；因此 capture_window_ms >= 1500 时调用方必然先收到 ECHO_TIMEOUT，
 * 尽管指令已经执行成功。
 */
class EchoWaitBudgetTest {

    private fun settings(
        dispatchMs: Long = 1500L,
        windowMs: Long = 400L,
        maxCaptureMs: Long = 2000L
    ) = EchoSettings(
        channels = EchoChannels.DEFAULT_CHANNELS,
        forced = emptyList(),
        dispatchTimeoutMs = dispatchMs,
        captureWindowMs = windowMs,
        maxCaptureMs = maxCaptureMs,
        includeLogPrefix = false,
        failurePatterns = EchoSettings.DEFAULT_FAILURE_PATTERNS,
        paperFeedback = true,
        log4j2 = true,
        jul = true,
        latestLog = true,
        nmsEnabled = false,
        proxySenderEnabled = false,
        rcon = RconSettings(false, "127.0.0.1", 25575, "", 3000),
        logCommands = false
    )

    @Test
    fun budgetIncludesCaptureWindow() {
        assertEquals(1900L, echoWaitBudgetMs(settings()))
    }

    @Test
    fun windowAboveMaxCaptureIsClamped() {
        assertEquals(3500L, echoWaitBudgetMs(settings(windowMs = 9000L)))
    }

    @Test
    fun zeroWindowOnlyWaitsForDispatch() {
        assertEquals(1500L, echoWaitBudgetMs(settings(windowMs = 0L)))
    }

    @Test
    fun budgetNeverBelowDispatchTimeout() {
        assertEquals(1000L, echoWaitBudgetMs(settings(dispatchMs = 1000L, windowMs = 0L)))
    }
}
