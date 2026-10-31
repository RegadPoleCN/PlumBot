// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

internal data class RconSettings(
    val enabled: Boolean,
    val host: String,
    val port: Int,
    val password: String,
    val timeoutMs: Int
)

/**
 * 不可变的配置快照：在调用线程（通常是异步的业务线程）读取一次后传给执行器，避免主线程
 * 执行期间反复解析 Configurate 节点，也避免配置热重载把一次执行拆成两套参数。
 */
internal data class EchoSettings(
    val channels: List<String>,
    val forced: List<String>,
    val dispatchTimeoutMs: Long,
    val captureWindowMs: Long,
    val maxCaptureMs: Long,
    val includeLogPrefix: Boolean,
    val failurePatterns: List<String>,
    val paperFeedback: Boolean,
    val log4j2: Boolean,
    val jul: Boolean,
    val latestLog: Boolean,
    val nmsEnabled: Boolean,
    val proxySenderEnabled: Boolean,
    val rcon: RconSettings,
    val logCommands: Boolean
) {
    companion object {
        val DEFAULT_FAILURE_PATTERNS: List<String> = listOf(
            "Unknown or incomplete command",
            "Unknown command",
            "未知的命令",
            "未知命令",
            "You do not have permission",
            "你没有权限"
        )
    }
}
