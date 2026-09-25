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
    override var filterManager: me.regadpole.plumbot.filter.FilterThesaurusManager? = null,
): PlatformContext {
    override val config: YamlConfigurator
        get() = configProvider()

    override val datasource: YamlConfigurator
        get() = datasourceProvider()

    override val platformType: PlatformType = PlatformType.BUKKIT

    override val supportedCapabilities: Set<me.regadpole.plumbot.platform.PlatformCapability> = setOf(
        me.regadpole.plumbot.platform.PlatformCapability.CHAT_RECEIVE,
        me.regadpole.plumbot.platform.PlatformCapability.CHAT_BROADCAST,
        me.regadpole.plumbot.platform.PlatformCapability.PRE_LOGIN_INTERCEPT,
        me.regadpole.plumbot.platform.PlatformCapability.PLAYER_JOIN_BROADCAST,
        me.regadpole.plumbot.platform.PlatformCapability.PLAYER_QUIT_BROADCAST,
        me.regadpole.plumbot.platform.PlatformCapability.PLAYER_DEATH_BROADCAST,
        me.regadpole.plumbot.platform.PlatformCapability.PLAYER_ADVANCEMENT_BROADCAST,
        me.regadpole.plumbot.platform.PlatformCapability.SERVER_TPS_METRICS,
        me.regadpole.plumbot.platform.PlatformCapability.COMMAND_DISPATCH
    )

    override fun isPluginAvailable(name: String): Boolean {
        return Bukkit.getPluginManager().isPluginEnabled(name)
    }
}
