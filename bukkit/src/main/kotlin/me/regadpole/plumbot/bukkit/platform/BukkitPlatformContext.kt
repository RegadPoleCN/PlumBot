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

package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.config.ConsoleEchoSetting
import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.EchoResult
import me.regadpole.plumbot.api.platform.EchoState
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.bukkit.echo.BukkitEchoExecutor
import me.regadpole.plumbot.bukkit.echo.EchoSettingsReader
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

class BukkitPlatformContext(
    private val configProvider: () -> YamlConfigurator,
    private val datasourceProvider: () -> YamlConfigurator,
    override val dataDirectory: Path,
    val logger: BukkitPlatformLogger,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService,
    val messenger: BukkitPlatformMessenger,
): PlatformContext {
    private val echoExecutor by lazy {
        BukkitEchoExecutor(
            settingsProvider = { EchoSettingsReader.read(config) },
            logger = this.logger,
            scheduler = this.scheduler
        )
    }

    override val config: YamlConfigurator
        get() = configProvider()

    override val datasource: YamlConfigurator
        get() = datasourceProvider()

    override val platformType: PlatformType = PlatformType.BUKKIT

    override val supportedCapabilities: Set<PlatformCapability> = setOf(
        PlatformCapability.CHAT_RECEIVE,
        PlatformCapability.CHAT_BROADCAST,
        PlatformCapability.PRE_LOGIN_INTERCEPT,
        PlatformCapability.PLAYER_JOIN_BROADCAST,
        PlatformCapability.PLAYER_QUIT_BROADCAST,
        PlatformCapability.PLAYER_DEATH_BROADCAST,
        PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST,
        PlatformCapability.SERVER_TPS_METRICS,
        PlatformCapability.COMMAND_DISPATCH
    )

    override fun log(level: LogLevel, message: String) {
        logger.log(level, message)
    }

    override fun sendMessage(message: Component) {
        messenger.sendMessage(message, ConsoleEchoSetting.enabled(config))
    }

    override fun isPluginAvailable(name: String): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled(name)
    }

    override fun getRecentTps(): DoubleArray? {
        return runCatching {
            val method = Bukkit.getServer().javaClass.getMethod("getTPS")
            method.invoke(Bukkit.getServer()) as? DoubleArray
        }.getOrNull()
    }

    override fun getMspt(): Double? {
        // 逐层降级：Paper 原生 getAverageTickTime → getTickTimes → NMS tickTimes 反射兜底。
        runCatching {
            val method = Bukkit.getServer().javaClass.getMethod("getAverageTickTime")
            val result = method.invoke(Bukkit.getServer())
            if (result is Number) return result.toDouble()
        }
        runCatching {
            val method = Bukkit.getServer().javaClass.getMethod("getTickTimes")
            val times = method.invoke(Bukkit.getServer()) as? LongArray
            if (times != null && times.isNotEmpty()) {
                return times.average() * 1.0E-6
            }
        }
        return runCatching {
            val mcServerClass = Class.forName("net.minecraft.server.MinecraftServer")
            val getServerMethod = mcServerClass.getMethod("getServer")
            val mcServer = getServerMethod.invoke(null)
            val field = mcServer.javaClass.getField("tickTimes")
            val times = field.get(mcServer) as? LongArray
            if (times != null && times.isNotEmpty()) {
                times.average() * 1.0E-6
            } else null
        }.getOrNull()
    }
    override fun dispatchConsoleCommandEcho(command: String): CompletableFuture<EchoResult> =
        echoExecutor.submit(command)

    override fun echoDiagnostics(): List<String> = echoExecutor.diagnostics()

    /**
     * 预热回显链路（幂等）。必须在**异步线程**调用：内部要做 Paper 通道探测、反射构造
     * FeedbackForwardingSender、解析 en_us，这些是首次派发时白花在主线程上的开销。
     */
    fun warmUpEcho() {
        echoExecutor.warmUp()
    }

    /**
     * 旧接口的兼容投影：新代码用 [dispatchConsoleCommandEcho]，这里把结构化结果压回
     * 旧字符串，保证未迁移调用方的行为（含原兜底文案）不变。
     */
    override fun dispatchConsoleCommand(command: String): CompletableFuture<String> =
        dispatchConsoleCommandEcho(command).thenApply { echo ->
            when (echo.state) {
                EchoState.CAPTURED -> echo.text
                EchoState.SUCCESS_NO_OUTPUT -> "指令已在主线程执行成功（无控制台回显）。"
                EchoState.UNKNOWN_OR_REJECTED -> echo.text.ifBlank { "指令执行失败或未识别此指令。" }
                EchoState.FAILED -> "执行指令出现异常: " + (
                    echo.detail?.takeIf { it.isNotBlank() } ?: echo.code ?: "未知错误"
                    )
            }
        }
}
