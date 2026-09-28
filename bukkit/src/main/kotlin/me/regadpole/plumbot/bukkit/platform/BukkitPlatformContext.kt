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

import me.regadpole.plumbot.config.YamlConfigurator
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import java.nio.file.Path

class BukkitPlatformContext(
    private val configProvider: () -> YamlConfigurator,
    private val datasourceProvider: () -> YamlConfigurator,
    override val dataDirectory: Path,
    val logger: BukkitPlatformLogger,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService,
    val messenger: BukkitPlatformMessenger,
): PlatformContext {
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
        messenger.sendMessage(message)
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
        // 1. 优先尝试 Paper 原生 getAverageTickTime()
        runCatching {
            val method = Bukkit.getServer().javaClass.getMethod("getAverageTickTime")
            val result = method.invoke(Bukkit.getServer())
            if (result is Number) return result.toDouble()
        }
        // 2. 尝试 Paper getTickTimes()
        runCatching {
            val method = Bukkit.getServer().javaClass.getMethod("getTickTimes")
            val times = method.invoke(Bukkit.getServer()) as? LongArray
            if (times != null && times.isNotEmpty()) {
                return times.average() * 1.0E-6
            }
        }
        // 3. 尝试 NMS MinecraftServer.tickTimes 反射兜底
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

    override fun dispatchConsoleCommand(command: String): java.util.concurrent.CompletableFuture<String> {
        val future = java.util.concurrent.CompletableFuture<String>()
        // 强制在游戏主线程调度执行，保证线程安全
        scheduler.runSync {
            try {
                val consoleSender = Bukkit.getConsoleSender()
                val capturingSender = CapturingConsoleCommandSender(consoleSender)
                logger.log(LogLevel.WARN, "[PlumBot-Security] 远程控制台正在执行指令: /$command")
                val success = Bukkit.dispatchCommand(capturingSender, command)
                val output = capturingSender.getOutput()
                val resultText = when {
                    output.isNotBlank() -> output
                    success -> "指令已在主线程执行成功（无控制台回显）。"
                    else -> "指令执行失败或未识别此指令。"
                }
                future.complete(resultText)
            } catch (e: Throwable) {
                future.complete("执行指令出现异常: ${e.message}")
            }
        }
        return future
    }
}
