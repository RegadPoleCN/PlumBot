// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.bukkit.echo.capture.echoWaitBudgetMs
import me.regadpole.plumbot.bukkit.echo.config.EchoSettings
import me.regadpole.plumbot.utils.MinecraftTranslationSource
import org.bukkit.Bukkit
import org.bukkit.command.ConsoleCommandSender

/** 最近一次**主线程**上花在派发链路的耗时，只用于诊断展示。 */
internal class MainThreadTiming(val prepareNanos: Long, val dispatchNanos: Long)

internal object EchoDiagnostics {

    fun render(
        settings: EchoSettings,
        paperFeedback: PaperFeedbackEcho,
        nms: NmsEcho,
        lastResult: EchoResult?,
        mainTiming: MainThreadTiming?
    ): List<String> {
        val lines = ArrayList<String>(12)
        val candidates = EchoChannelSelector.candidates(settings)
        lines += "候选通道: " + candidates.joinToString(" -> ").ifBlank { "（未配置）" }
        if (settings.forced.isNotEmpty()) {
            lines += "强制通道: " + settings.forced.joinToString(", ") + "（失败不会自动换通道）"
        }
        lines += "paper_feedback: " + availability(paperFeedback.available, paperFeedback.unavailableReason)
        lines += "plugin_command: 可用（按 label 查找插件命令；找不到则换下一通道）"
        lines += "console: 可用（真实控制台派发 + ConsoleCommandSender 代理兜底）"
        lines += "log4j2: " + (if (settings.log4j2) "已启用（派发时 attach）" else "已禁用")
        lines += "JUL: " + (if (settings.jul) "已启用（派发时 attach）" else "已禁用")
        val latestLog = java.io.File("logs/latest.log")
        lines += "latest.log: " + when {
            !settings.latestLog -> "已禁用"
            latestLog.isFile && latestLog.canRead() -> "可读（" + latestLog.absolutePath + "）"
            else -> "不可读（" + latestLog.absolutePath + "）"
        }
        lines += "rcon: " + if (settings.rcon.enabled) {
            "已启用（" + settings.rcon.host + ":" + settings.rcon.port + "，超时 " + settings.rcon.timeoutMs + "ms）"
        } else "已禁用"
        lines += "nms: " + if (settings.nmsEnabled) availability(nms.available, nms.unavailableReason) else "已禁用"
        lines += "minecraft_translations: " + MinecraftTranslationSource.status()
        lines += "proxy_sender: " + if (settings.proxySenderEnabled) "已启用（实验通道，Paper 可能仍丢 sender）" else "已禁用"
        lines += "线程模型: " + (if (Bukkit.isPrimaryThread()) "当前在主线程" else "当前在异步线程") +
            "；派发超时 " + settings.dispatchTimeoutMs + "ms，捕获窗口 " + settings.captureWindowMs +
            "ms，调用方等待上限 " + echoWaitBudgetMs(settings) + "ms"
        val last = lastResult
        lines += if (last == null) {
            "最近一次: 本会话尚未执行远程指令"
        } else {
            "最近一次: state=" + last.state + " channel=" + last.channel +
                (last.code?.let { " code=" + it } ?: "") +
                " duration=" + last.durationMs + "ms lines=" + last.lines.size +
                (mainTiming?.let { timing ->
                    " main=" + millisText(timing.prepareNanos + timing.dispatchNanos) +
                        "(prepare=" + millisText(timing.prepareNanos) +
                        " dispatch=" + millisText(timing.dispatchNanos) + ")"
                } ?: "") +
                (last.detail?.takeIf { it.isNotBlank() }?.let { " detail=" + it } ?: "")
        }
        return lines
    }

    /** 纳秒转带一位小数的毫秒文本（避免为格式化引入 Locale）。 */
    private fun millisText(nanos: Long): String = ((nanos / 100_000L).toDouble() / 10.0).toString() + "ms"

    private fun availability(available: Boolean, reason: String): String =
        if (available) "可用" else "不可用（" + reason + "）"
}
