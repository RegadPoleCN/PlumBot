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
}
