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

    override fun dispatchConsoleCommand(command: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        val capturer = CapturingVelocityCommandSource(server.consoleCommandSource)
        server.commandManager.executeAsync(capturer, command).thenAccept { _ ->
            future.complete(capturer.getOutput())
        }.exceptionally { err ->
            future.complete("执行指令出现异常: ${err.message}")
            null
        }
        return future
    }
}
