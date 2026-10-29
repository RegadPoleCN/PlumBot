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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.internal.ConsoleText
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.internal.RemoteConsoleGuard
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.utils.OutputCapturingBuffer

/**
 * 远程控制台指令：管理员鉴权、限流与并发闸门、回显归一化与截断。
 * 图片与文字共用同一份 card.text，图挂了直接发文字，不会出现两套内容。
 */
class RemoteConsoleCommandService(private val service: BotCommandService) {
    private companion object {
        /** 未配置时的默认回显上限。 */
        const val DEFAULT_MAX_OUTPUT = 1500
    }

    fun execute(command: String, groupId: Long, userId: Long) {
        val remoteConsoleEnabled = service.context.config.getBoolean("feature", "remote_console", "enable")
        if (!remoteConsoleEnabled) {
            service.sendRawMessage(groupId, Messages.remoteCommandDisabled)
            return
        }

        val admins = service.context.config.getLongList("admins")
        if (userId !in admins) {
            service.context.log(
                LogLevel.WARN,
                "[PlumBot-Security] 非管理员 QQ ($userId) 尝试在群 ($groupId) 执行远程指令: $command (已拦截)"
            )
            service.sendRawMessage(groupId, Messages.remoteCommandDenied)
            return
        }

        if (!service.context.hasCapability(PlatformCapability.COMMAND_DISPATCH)) {
            service.sendRawMessage(groupId, "当前平台不支持远程指令调度执行。")
            return
        }

        val trimmedCmd = command.trim().removePrefix("/")
        if (trimmedCmd.isBlank()) {
            service.sendRawMessage(groupId, "请输入有效的控制台指令内容！")
            return
        }

        // 两道闸门：只看"是不是管理员"不够——账号被盗或误操作同样能打满服务端。
        // 固定住本次使用的实例：release 必须落在同一个实例上，否则 reload 后在新实例释放、
        // 旧实例的 inFlight 永远不归零，并发上限被逐步锁死。
        val activeGuard = guard()
        when (activeGuard.tryAcquire(userId)) {
            RemoteConsoleGuard.Decision.RateLimited -> {
                service.sendRawMessage(groupId, "操作过于频繁，请稍后再试。")
                return
            }

            RemoteConsoleGuard.Decision.TooManyConcurrent -> {
                service.sendRawMessage(groupId, "当前并发执行的指令过多，请稍后再试。")
                return
            }

            RemoteConsoleGuard.Decision.Allowed -> Unit
        }

        val configured = service.context.config.getInteger("feature", "remote_console", "max_output_length")
            .let { if (it <= 0) DEFAULT_MAX_OUTPUT else it }
        // 两级截断联动：平台侧捕获缓冲有硬上限，配置写超过它时**必须显式告知**，
        // 否则使用者会看到"没到配置上限却已截断"，无从判断是配置没生效还是输出被吞了。
        val maxLength = if (configured > OutputCapturingBuffer.DEFAULT_MAX_LENGTH) {
            warnOnce(
                "[PlumBot] feature.remote_console.max_output_length=$configured 超过平台捕获上限 " +
                    "${OutputCapturingBuffer.DEFAULT_MAX_LENGTH}，已按上限生效。"
            )
            OutputCapturingBuffer.DEFAULT_MAX_LENGTH
        } else {
            configured
        }

        val echoFuture = try {
            service.context.dispatchConsoleCommandEcho(trimmedCmd)
        } catch (err: Throwable) {
            // 同步抛异常时没有任何 future 回调，必须在这里释放，否则并发额度直接泄漏。
            activeGuard.release()
            service.sendRawMessage(groupId, "执行指令时异常: ${err.message}")
            return
        }

        echoFuture
            .whenComplete { _, _ -> activeGuard.release() }
            .thenAccept { echo ->
                // 归一化必须在截断**之前**：控制序列若先占用额度，真正的内容会被挤掉。
                // EchoResult.lines 已在平台侧投影为 § 色码，这里只清终端控制序列，不动 §。
                val cleaned = ConsoleText.sanitize(echo.lines.joinToString("\n"))
                val truncatedOutput = if (cleaned.length > maxLength) {
                    cleaned.substring(0, maxLength) + "\n... (输出已截断)"
                } else {
                    cleaned
                }

                val rendered = renderEcho(echo, truncatedOutput)
                service.sendCard(
                    groupId = groupId,
                    route = RenderRoute.CONSOLE,
                    card = RenderCards.console(
                        text = rendered,
                        title = null,
                        state = echo.state.name,
                        channel = echo.channel,
                        code = echo.code,
                        durationMs = echo.durationMs
                    )
                )
            }.exceptionally { err ->
                service.sendRawMessage(groupId, "执行指令时异常: ${err.message}")
                null
            }
    }

    /** 按执行状态选择文案：把"已执行但无回显"与"执行失败"分开告知。 */
    private fun renderEcho(echo: EchoResult, output: String): String {
        val shown = output.ifBlank { "（无）" }
        return when (echo.state) {
            EchoState.CAPTURED -> service.render(Messages.remoteCommandExecuted, "%output%" to output)
            EchoState.SUCCESS_NO_OUTPUT -> service.render(
                Messages.remoteCommandNoOutput,
                "%channel%" to echo.channel,
                "%duration%" to echo.durationMs.toString()
            )
            EchoState.UNKNOWN_OR_REJECTED -> service.render(
                Messages.remoteCommandUnknown,
                "%channel%" to echo.channel,
                "%output%" to shown
            )
            EchoState.FAILED -> service.render(
                Messages.remoteCommandFailed,
                "%channel%" to echo.channel,
                "%code%" to (echo.code ?: "UNKNOWN"),
                "%detail%" to (echo.detail ?: "无详细信息"),
                "%output%" to shown
            )
        }
    }

    /**
     * 闸门实例：**按配置在首次使用时构造**（配置可在 reload 后变化）。不能每次调用新建，
     * 否则限流形同虚设（桶每次都是满的）；配置变化时重建，旧实例的在途计数随之丢弃。
     */
    private fun guard(): RemoteConsoleGuard {
        val perMinute = service.context.config.getInteger("feature", "remote_console", "rate_limit_per_minute")
            .let { if (it < 0) 10 else it }
        val maxConcurrent = service.context.config.getInteger("feature", "remote_console", "max_concurrent")
            .let { if (it < 0) 4 else it }

        val current = cached
        if (current != null && current.perMinuteValue == perMinute && current.maxConcurrentValue == maxConcurrent) {
            return current.guard
        }
        return synchronized(this) {
            val again = cached
            if (again != null && again.perMinuteValue == perMinute && again.maxConcurrentValue == maxConcurrent) {
                again.guard
            } else {
                RemoteConsoleGuard(perMinute, maxConcurrent).also { cached = Cached(perMinute, maxConcurrent, it) }
            }
        }
    }

    /** 一次性告警（避免高频路径刷屏）。 */
    private fun warnOnce(message: String) {
        if (warned.add(message)) service.context.log(LogLevel.WARN, message)
    }

    private val warned = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private data class Cached(val perMinuteValue: Int, val maxConcurrentValue: Int, val guard: RemoteConsoleGuard)

    @Volatile
    private var cached: Cached? = null
}