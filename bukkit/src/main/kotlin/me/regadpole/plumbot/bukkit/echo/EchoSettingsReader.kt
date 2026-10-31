// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.config.YamlConfigurator

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
