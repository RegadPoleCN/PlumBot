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

package me.regadpole.plumbot.velocity.platform

import com.velocitypowered.api.proxy.ProxyServer
import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.internal.RemoteConsoleAudit
import me.regadpole.plumbot.api.platform.*
import me.regadpole.plumbot.config.YamlConfigurator
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class VelocityPlatformContext(
    val server: ProxyServer,
    private val slf4jLogger: Logger,
    private val debugProvider: DebugProvider,
    override val dataDirectory: Path,
    private val configProvider: () -> YamlConfigurator,
    private val datasourceProvider: () -> YamlConfigurator,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService
) : PlatformContext {

    override val config: YamlConfigurator get() = configProvider()
    override val datasource: YamlConfigurator get() = datasourceProvider()
    override val platformType: PlatformType = PlatformType.VELOCITY

    override val supportedCapabilities: Set<PlatformCapability> = setOf(
        PlatformCapability.CHAT_RECEIVE,
        PlatformCapability.CHAT_BROADCAST,
        PlatformCapability.PRE_LOGIN_INTERCEPT,
        PlatformCapability.PLAYER_JOIN_BROADCAST,
        PlatformCapability.PLAYER_QUIT_BROADCAST,
        PlatformCapability.SERVER_SWITCH_BROADCAST,
        PlatformCapability.COMMAND_DISPATCH
    )

    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.TRACE -> {
                slf4jLogger.trace(message)
                debugProvider.log(message)
            }
            LogLevel.DEBUG -> {
                slf4jLogger.debug(message)
                debugProvider.log(message)
            }
            LogLevel.INFO -> slf4jLogger.info(message)
            LogLevel.WARN -> slf4jLogger.warn(message)
            LogLevel.ERROR, LogLevel.FATAL -> slf4jLogger.error(message)
        }
    }

    override fun sendMessage(message: Component) {
        server.sendMessage(message)
    }

    override fun isPluginAvailable(name: String): Boolean =
        server.pluginManager.isLoaded(name.lowercase())
    /**
     * 审计是否记录**完整指令**（配置键 `feature.remote_console.log_commands`）。
     * 键缺失按"隐藏明细"处理：安全侧失败模式是少记而不是多记；正常安装下该键由
     * 内置模板与 `ConfigMigrator` 补齐（默认 `true`）。
     */
    private fun logCommandsEnabled(): Boolean =
        config.getBoolean("feature", "remote_console", "log_commands")

    override fun dispatchConsoleCommand(command: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val capturer = CapturingVelocityCommandSource(server.consoleCommandSource)
        // 与 Bukkit 侧保持同一份审计日志：远程控制台是高风险能力，
        // 三端都必须在执行前留痕，否则"代理端执行了什么"无从追溯。
        log(LogLevel.WARN, RemoteConsoleAudit.describe(command, logCommandsEnabled()))
        server.commandManager.executeAsync(capturer, command).thenAccept { _ ->
            future.complete(capturer.getOutput())
        }.exceptionally { err ->
            future.complete("执行指令出现异常: ${err.message}")
            null
        }
        return future
    }

    /**
     * 结构化回显：把 `executeAsync` 的布尔结果与捕获行映射成 [EchoResult]，才能区分
     * "执行成功但无回显"和"指令未识别"，并带上真实通道名 `velocity`。
     * 旧 [dispatchConsoleCommand] 仍保留字符串契约，供未迁移的调用方使用。
     */
    override fun dispatchConsoleCommandEcho(command: String): CompletableFuture<EchoResult> {
        val startNanos = System.nanoTime()
        val capturer = CapturingVelocityCommandSource(server.consoleCommandSource)
        log(LogLevel.WARN, RemoteConsoleAudit.describe(command, logCommandsEnabled()))
        val future = CompletableFuture<EchoResult>()
        server.commandManager.executeAsync(capturer, command).whenComplete { executed, err ->
            val lines = capturer.getOutputOrEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            future.complete(
                when {
                    err != null -> EchoResult(
                        state = EchoState.FAILED,
                        channel = "velocity",
                        lines = lines,
                        durationMs = durationMs,
                        code = EchoCode.ECHO_EXCEPTION,
                        detail = err.message?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                    )
                    executed == false -> EchoResult(
                        state = EchoState.UNKNOWN_OR_REJECTED,
                        channel = "velocity",
                        lines = lines,
                        durationMs = durationMs,
                        code = EchoCode.ECHO_DISPATCH_FALSE,
                        detail = "代理端未识别或拒绝该指令"
                    )
                    lines.isNotEmpty() -> EchoResult(
                        state = EchoState.CAPTURED,
                        channel = "velocity",
                        lines = lines,
                        durationMs = durationMs
                    )
                    else -> EchoResult(
                        state = EchoState.SUCCESS_NO_OUTPUT,
                        channel = "velocity",
                        durationMs = durationMs,
                        code = EchoCode.ECHO_NO_CHANNEL,
                        detail = "代理端执行成功，但没有捕获到回显"
                    )
                }
            )
        }
        return future
    }
}
