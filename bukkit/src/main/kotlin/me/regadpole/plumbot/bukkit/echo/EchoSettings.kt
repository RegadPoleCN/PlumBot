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

import me.regadpole.plumbot.config.YamlConfigurator

/** 远程回显通道的稳定标识，与 config.yml 中的字符串一一对应。 */
internal object EchoChannels {
    const val PAPER_FEEDBACK = "paper_feedback"
    const val PLUGIN_COMMAND = "plugin_command"
    const val CONSOLE = "console"
    const val RCON = "rcon"
    const val NMS = "nms"
    const val PROXY_SENDER = "proxy_sender"

    val KNOWN: Set<String> = linkedSetOf(
        PAPER_FEEDBACK,
        PLUGIN_COMMAND,
        CONSOLE,
        RCON,
        NMS,
        PROXY_SENDER
    )

    val DEFAULT_CHANNELS: List<String> = listOf(PAPER_FEEDBACK, PLUGIN_COMMAND, CONSOLE)
}

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

/**
 * 从 [YamlConfigurator] 读取 feature.remote_console.echo.*。
 *
 * 缺失值用内置默认，空列表视为未配置；每个布尔键显式区分 virtual 节点与 false，因此
 * log4j2=false 不会被误当成"未配置所以默认 true"。
 */
internal object EchoSettingsReader {

    private const val ROOT = "feature.remote_console.echo"

    fun read(config: YamlConfigurator): EchoSettings {
        val channels = readList(config, "$ROOT.channels").ifEmpty { EchoChannels.DEFAULT_CHANNELS }
        val forced = readList(config, "$ROOT.forced")
        val failurePatterns = readList(config, "$ROOT.failure_patterns")
            .ifEmpty { EchoSettings.DEFAULT_FAILURE_PATTERNS }
        return EchoSettings(
            channels = channels,
            forced = forced,
            dispatchTimeoutMs = positiveLong(config, "$ROOT.dispatch_timeout_ms", 1500L),
            captureWindowMs = nonNegativeLong(config, "$ROOT.capture_window_ms", 400L),
            maxCaptureMs = positiveLong(config, "$ROOT.max_capture_ms", 2000L),
            includeLogPrefix = booleanOr(config, "$ROOT.include_log_prefix", false),
            failurePatterns = failurePatterns,
            paperFeedback = booleanOr(config, "$ROOT.paper_feedback", true),
            log4j2 = booleanOr(config, "$ROOT.log.log4j2", true),
            jul = booleanOr(config, "$ROOT.log.jul", true),
            latestLog = booleanOr(config, "$ROOT.log.latest_log", true),
            nmsEnabled = booleanOr(config, "$ROOT.nms.enabled", false),
            proxySenderEnabled = booleanOr(config, "$ROOT.proxy_sender.enabled", false),
            rcon = RconSettings(
                enabled = booleanOr(config, "$ROOT.rcon.enabled", false),
                host = config.getString("$ROOT.rcon.host")?.takeIf { it.isNotBlank() } ?: "127.0.0.1",
                port = config.getInteger("$ROOT.rcon.port").takeIf { it in 1..65535 } ?: 25575,
                password = config.getString("$ROOT.rcon.password") ?: "",
                timeoutMs = positiveInt(config, "$ROOT.rcon.timeout_ms", 3000)
            ),
            logCommands = booleanOr(config, "feature.remote_console.log_commands", false)
        )
    }

    private fun readList(config: YamlConfigurator, path: String): List<String> =
        config.getStringList(path)
            .mapNotNull { it }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun booleanOr(config: YamlConfigurator, path: String, default: Boolean): Boolean {
        return if (!config.contains(path)) default else config.getBoolean(path)
    }

    private fun positiveLong(config: YamlConfigurator, path: String, default: Long): Long {
        if (!config.contains(path)) return default
        val value = config.getLong(path)
        return if (value > 0) value else default
    }

    private fun nonNegativeLong(config: YamlConfigurator, path: String, default: Long): Long {
        if (!config.contains(path)) return default
        return config.getLong(path).coerceAtLeast(0L)
    }

    private fun positiveInt(config: YamlConfigurator, path: String, default: Int): Int {
        if (!config.contains(path)) return default
        val value = config.getInteger(path)
        return if (value > 0) value else default
    }
}