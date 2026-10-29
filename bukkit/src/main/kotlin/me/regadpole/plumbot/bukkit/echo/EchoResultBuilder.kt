/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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