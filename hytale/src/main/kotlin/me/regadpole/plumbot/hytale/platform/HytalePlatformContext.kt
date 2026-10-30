// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandManager
import com.hypixel.hytale.server.core.console.ConsoleSender
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.commands.world.perf.WorldPerfCommand
import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.internal.RemoteConsoleAudit
import me.regadpole.plumbot.api.platform.*
import me.regadpole.plumbot.config.ConsoleEchoSetting
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.hytale.PlumBotHytale
import me.regadpole.plumbot.utils.toPlainText
import net.kyori.adventure.text.Component
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class HytalePlatformContext(
    val plugin: PlumBotHytale,
    override val dataDirectory: Path,
    private val configProvider: () -> YamlConfigurator,
    private val datasourceProvider: () -> YamlConfigurator,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService,
    private val debugProvider: DebugProvider
) : PlatformContext {

    override val config: YamlConfigurator get() = configProvider()
    override val datasource: YamlConfigurator get() = datasourceProvider()
    override val platformType: PlatformType = PlatformType.HYTALE

    override val supportedCapabilities: Set<PlatformCapability> = setOf(
        PlatformCapability.CHAT_RECEIVE,
        PlatformCapability.CHAT_BROADCAST,
        PlatformCapability.PRE_LOGIN_INTERCEPT,
        PlatformCapability.PLAYER_JOIN_BROADCAST,
        PlatformCapability.PLAYER_QUIT_BROADCAST,
        PlatformCapability.COMMAND_DISPATCH,
        PlatformCapability.SERVER_TPS_METRICS
    )

    /**
     * 分发一条平台日志：定级与文本由 [HytaleLogMapping] 决定。
     *
     * Hytale 宿主控制台默认只显示 INFO 及以上，旧实现把 DEBUG/TRACE 写成 atFine()，
     * 于是 `debug.enable: true` 打开后控制台仍一行调试日志都看不到。
     */
    override fun log(level: LogLevel, message: String) {
        val line = HytaleLogMapping.map(level, message, debugProvider.isEnabled)
        // debug.log 只收 DEBUG/TRACE（与旧行为一致）；DebugProvider 自身在未启用时忽略。
        if (line.appendToDebugFile) debugProvider.log(message)
        val logger = plugin.logger
        when (line.level) {
            HytaleConsoleLevel.FINEST -> logger.atFinest().log(line.text)
            HytaleConsoleLevel.FINE -> logger.atFine().log(line.text)
            HytaleConsoleLevel.INFO -> logger.atInfo().log(line.text)
            HytaleConsoleLevel.WARNING -> logger.atWarning().log(line.text)
            HytaleConsoleLevel.SEVERE -> logger.atSevere().log(line.text)
        }
    }

    /**
     * 播报一条消息：广播给全服玩家，并按 `feature.message.console_echo` 写入宿主控制台。
     *
     * 必须单独写控制台：`Universe.get().sendMessage(...)` 只投递给在线玩家，控制台一行
     * 都收不到。控制台写入失败只记 WARN：玩家侧广播已发生，不回滚也不重发。
     */
    override fun sendMessage(message: Component) {
        val payload = Message.raw(message.toPlainText())
        Universe.get().sendMessage(payload)
        if (!ConsoleEchoSetting.enabled(config)) return
        runCatching { ConsoleSender.INSTANCE.sendMessage(payload) }
            .onFailure { log(LogLevel.WARN, "写入控制台失败（消息已广播给玩家）：${it.message}") }
    }

    override fun isPluginAvailable(name: String): Boolean {
        return runCatching {
            val identifier = com.hypixel.hytale.common.plugin.PluginIdentifier.fromString(name)
            com.hypixel.hytale.server.core.plugin.PluginManager.get().getPlugin(identifier)?.isEnabled ?: false
        }.getOrDefault(false)
    }

    override fun getRecentTps(): DoubleArray? {
        return runCatching {
            val world = Universe.get().defaultWorld ?: return null
            val metric = world.bufferedTickLengthMetricSet
            val avgNanos = metric.getAverage(0)
            val tps = WorldPerfCommand.tpsFromDelta(avgNanos, world.tickStepNanos.toLong())
            doubleArrayOf(tps, tps, tps)
        }.getOrNull()
    }

    override fun getMspt(): Double? {
        return runCatching {
            val world = Universe.get().defaultWorld ?: return null
            world.bufferedTickLengthMetricSet.getAverage(0) * 1.0E-6
        }.getOrNull()
    }
    /**
     * 审计是否记录**完整指令**（配置键 `feature.remote_console.log_commands`）。
     * 键缺失按"隐藏明细"处理：安全侧失败模式是少记而不是多记；正常安装下该键由
     * 内置模板与 `ConfigMigrator` 补齐（默认 `true`）。
     */
    private fun logCommandsEnabled(): Boolean =
        config.getBoolean("feature", "remote_console", "log_commands")

    override fun dispatchConsoleCommand(command: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val capturer = CapturingHytaleCommandSender()
        // 与 Bukkit / Velocity 侧同一份审计日志：远程控制台是高风险能力，执行前必须留痕。
        log(LogLevel.WARN, RemoteConsoleAudit.describe(command, logCommandsEnabled()))
        CommandManager.get().handleCommand(capturer, command).thenAccept {
            future.complete(capturer.getOutput())
        }.exceptionally { err ->
            future.complete("执行指令出现异常: ${err.message}")
            null
        }
        return future
    }

    /**
     * 结构化回显：`handleCommand` 只用完成/异常表达结果，没有布尔返回值 ——
     * 完成且无异常即视为已执行，有捕获行则 CAPTURED，否则 SUCCESS_NO_OUTPUT。
     * 旧 [dispatchConsoleCommand] 保留给未迁移的调用方。
     */
    override fun dispatchConsoleCommandEcho(command: String): CompletableFuture<EchoResult> {
        val startNanos = System.nanoTime()
        val capturer = CapturingHytaleCommandSender()
        log(LogLevel.WARN, RemoteConsoleAudit.describe(command, logCommandsEnabled()))
        val future = CompletableFuture<EchoResult>()
        CommandManager.get().handleCommand(capturer, command).whenComplete { _, err ->
            val lines = capturer.getOutputOrEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            future.complete(
                when {
                    err != null -> EchoResult(
                        state = EchoState.FAILED,
                        channel = "hytale",
                        lines = lines,
                        durationMs = durationMs,
                        code = EchoCode.ECHO_EXCEPTION,
                        detail = err.message?.takeIf { it.isNotBlank() } ?: err.javaClass.simpleName
                    )
                    lines.isNotEmpty() -> EchoResult(
                        state = EchoState.CAPTURED,
                        channel = "hytale",
                        lines = lines,
                        durationMs = durationMs
                    )
                    else -> EchoResult(
                        state = EchoState.SUCCESS_NO_OUTPUT,
                        channel = "hytale",
                        durationMs = durationMs,
                        code = EchoCode.ECHO_NO_CHANNEL,
                        detail = "Hytale 执行成功，但没有捕获到回显"
                    )
                }
            )
        }
        return future
    }
}
