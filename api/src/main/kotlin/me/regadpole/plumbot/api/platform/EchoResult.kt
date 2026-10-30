// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi
import java.util.concurrent.CompletableFuture

/**
 * 远程控制台一次执行的最终状态。
 *
 * 把"是否真正执行""是否捕获到回显"与"失败原因"分开表达，业务层才能给出准确提示，
 * 而不是把"没有回显"误报成"执行失败"。
 */
@StableApi
enum class EchoState {
    /** 指令已被服务器识别并执行，且至少捕获到一行回显。 */
    CAPTURED,

    /** 指令已被服务器识别并执行，但所有捕获通道都没有拿到回显（例如输出只进了异步日志）。 */
    SUCCESS_NO_OUTPUT,

    /** `dispatchCommand` 返回 false，或日志显示指令不存在 / 被拒绝。 */
    UNKNOWN_OR_REJECTED,

    /** 执行过程本身异常：超时、通道不可用、RCON 认证失败等。 */
    FAILED
}

/**
 * 一次远程控制台执行的完整结果。
 *
 * @param channel 实际产出回显（或最终决定状态）的通道标识，便于 `/plumbot doctor` 定位
 * @param lines 已投影为 `§` 色码的纯文本回显行（不含换行符）
 * @param code 稳定错误码，见 [EchoCode]；成功时可为空
 * @param detail 面向管理员的人类可读补充说明（异常信息、通道缺失原因等）
 */
@StableApi
data class EchoResult(
    val state: EchoState,
    val channel: String,
    val lines: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val code: String? = null,
    val detail: String? = null
) {
    /** 多行回显合并文本，行间以 `\n` 分隔。 */
    val text: String
        get() = lines.joinToString("\n")

    companion object {
        private val EXCEPTION_PREFIX = "执行指令出现异常"

        /**
         * 兼容旧平台实现的字符串返回值，只用于尚未迁移到 [EchoResult] 的平台；正式实现必须
         * 自行给出 Channel 与状态。
         */
        fun fromLegacyOutput(output: String): EchoResult {
            val trimmed = output.trim()
            return when {
                trimmed.isEmpty() -> EchoResult(
                    state = EchoState.SUCCESS_NO_OUTPUT,
                    channel = "legacy",
                    code = EchoCode.ECHO_NO_CHANNEL,
                    detail = "平台未返回回显文本"
                )
                trimmed.startsWith(EXCEPTION_PREFIX) -> EchoResult(
                    state = EchoState.FAILED,
                    channel = "legacy",
                    lines = listOf(trimmed),
                    code = EchoCode.ECHO_EXCEPTION,
                    detail = trimmed
                )
                trimmed.contains("失败或未识别") -> EchoResult(
                    state = EchoState.UNKNOWN_OR_REJECTED,
                    channel = "legacy",
                    lines = listOf(trimmed),
                    code = EchoCode.ECHO_UNKNOWN_COMMAND
                )
                else -> EchoResult(
                    state = EchoState.CAPTURED,
                    channel = "legacy",
                    lines = trimmed.split('\n')
                )
            }
        }

        /** 平台完全不具备远程执行能力时的默认返回值。 */
        fun unsupported(): EchoResult = EchoResult(
            state = EchoState.FAILED,
            channel = "none",
            code = EchoCode.ECHO_NO_CHANNEL,
            detail = "当前平台不支持指令执行回显"
        )
    }
}

/** 远程回显的稳定错误码，供消息模板、doctor 与日志检索共用。 */
@StableApi
object EchoCode {
    const val ECHO_NO_CHANNEL = "ECHO_NO_CHANNEL"
    const val ECHO_CHANNEL_UNAVAILABLE = "ECHO_CHANNEL_UNAVAILABLE"
    const val ECHO_TIMEOUT = "ECHO_TIMEOUT"
    const val ECHO_UNKNOWN_COMMAND = "ECHO_UNKNOWN_COMMAND"
    const val ECHO_DISPATCH_FALSE = "ECHO_DISPATCH_FALSE"
    const val ECHO_EXCEPTION = "ECHO_EXCEPTION"
    const val ECHO_RCON_AUTH_FAILED = "ECHO_RCON_AUTH_FAILED"
}

/**
 * 平台上下文中的远程执行入口扩展：保持默认方法，旧平台实现无需改动；
 * 新实现应覆盖 [PlatformContext.dispatchConsoleCommandEcho]。
 */
@StableApi
fun PlatformContext.dispatchEcho(command: String): CompletableFuture<EchoResult> =
    dispatchConsoleCommandEcho(command)