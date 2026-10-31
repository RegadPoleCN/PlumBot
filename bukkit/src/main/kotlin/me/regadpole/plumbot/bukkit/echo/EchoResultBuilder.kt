// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoCode
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState

/**
 * 把捕获结果映射为 [EchoResult] 的纯函数。
 *
 * 纯函数便于单测，也避免执行器在 finalize 路径上再依赖 Bukkit。
 */
internal object EchoResultBuilder {

    fun build(
        channel: String,
        lines: List<String>,
        dispatchOk: Boolean,
        exception: Throwable?,
        failurePatterns: List<String>,
        durationMs: Long,
        detail: String? = null
    ): EchoResult {
        if (exception != null) {
            return EchoResult(
                state = EchoState.FAILED,
                channel = channel,
                lines = lines,
                durationMs = durationMs,
                code = EchoCode.ECHO_EXCEPTION,
                detail = detail ?: exception.message?.takeIf { it.isNotBlank() }
                    ?: exception.javaClass.simpleName
            )
        }

        val matchedFailure = failurePatterns.firstOrNull { pattern ->
            pattern.isNotBlank() && lines.any { it.contains(pattern, ignoreCase = true) }
        }
        if (matchedFailure != null) {
            return EchoResult(
                state = EchoState.UNKNOWN_OR_REJECTED,
                channel = channel,
                lines = lines,
                durationMs = durationMs,
                code = EchoCode.ECHO_UNKNOWN_COMMAND,
                detail = detail ?: "回显命中失败模式: " + matchedFailure.take(80)
            )
        }

        if (!dispatchOk) {
            // 未识别/被拒绝但服务端仍可能打印 usage 或错误文本：保留 lines，状态必须是 UNKNOWN_OR_REJECTED。
            return EchoResult(
                state = EchoState.UNKNOWN_OR_REJECTED,
                channel = channel,
                lines = lines,
                durationMs = durationMs,
                code = EchoCode.ECHO_DISPATCH_FALSE,
                detail = detail ?: "服务器未识别或拒绝该指令"
            )
        }

        if (lines.isNotEmpty()) {
            return EchoResult(
                state = EchoState.CAPTURED,
                channel = channel,
                lines = lines,
                durationMs = durationMs,
                detail = detail
            )
        }

        return EchoResult(
            state = EchoState.SUCCESS_NO_OUTPUT,
            channel = channel,
            durationMs = durationMs,
            code = EchoCode.ECHO_NO_CHANNEL,
            detail = detail
        )
    }
}
