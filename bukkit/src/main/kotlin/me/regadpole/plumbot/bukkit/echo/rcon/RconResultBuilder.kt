// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo.rcon

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.bukkit.echo.config.EchoSettings
import me.regadpole.plumbot.utils.ComponentProjector

internal object RconResultBuilder {

    fun build(channel: String, outcome: RconOutcome, settings: EchoSettings, durationMs: Long): EchoResult {
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
}
