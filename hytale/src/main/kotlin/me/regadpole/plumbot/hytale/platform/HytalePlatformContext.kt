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

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandManager
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.commands.world.perf.WorldPerfCommand
import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.api.platform.*
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

    override fun log(level: LogLevel, message: String) {
        val logger = plugin.logger
        when (level) {
            LogLevel.TRACE, LogLevel.DEBUG -> {
                logger.atFine().log(message)
                debugProvider.log(message)
            }
            LogLevel.INFO -> logger.atInfo().log(message)
            LogLevel.WARN -> logger.atWarning().log(message)
            LogLevel.ERROR, LogLevel.FATAL -> logger.atSevere().log(message)
        }
    }

    override fun sendMessage(message: Component) {
        Universe.get().sendMessage(Message.raw(message.toPlainText()))
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

    override fun dispatchConsoleCommand(command: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val capturer = CapturingHytaleCommandSender()
        CommandManager.get().handleCommand(capturer, command).thenAccept {
            future.complete(capturer.getOutput())
        }.exceptionally { err ->
            future.complete("执行指令出现异常: ${err.message}")
            null
        }
        return future
    }
}
