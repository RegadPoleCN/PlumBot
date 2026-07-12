package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformMessenger
import me.regadpole.plumbot.platform.PlatformPlayerService
import me.regadpole.plumbot.platform.PlatformScheduler
import me.regadpole.plumbot.platform.PlatformType
import org.bukkit.Bukkit
import java.nio.file.Path

class BukkitPlatformContext(
    private val configProvider: () -> YamlConfigurator,
    private val datasourceProvider: () -> YamlConfigurator,
    override val dataDirectory: Path,
    override val logger: BukkitPlatformLogger,
    override val scheduler: PlatformScheduler,
    override val playerService: PlatformPlayerService,
    override val messenger: PlatformMessenger,
): PlatformContext {
    override val config: YamlConfigurator
        get() = configProvider()

    override val datasource: YamlConfigurator
        get() = datasourceProvider()

    override val platformType: PlatformType = PlatformType.BUKKIT

    override fun isPluginAvailable(name: String): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled(name)
    }
}
