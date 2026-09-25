package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.config.YamlConfigurator
import java.nio.file.Path

@StableApi
interface PlatformContext {
    val config: YamlConfigurator
    val datasource: YamlConfigurator
    val dataDirectory: Path
    val logger: PlatformLogger
    val scheduler: PlatformScheduler
    val playerService: PlatformPlayerService
    val messenger: PlatformMessenger
    val platformType: PlatformType

    /** 当前运行平台所声明支持的全部特性能力集合 */
    val supportedCapabilities: Set<PlatformCapability>
        get() = emptySet()

    fun isPluginAvailable(name: String): Boolean

    /** 校验当前平台是否具备某个能力 */
    fun hasCapability(capability: PlatformCapability): Boolean =
        capability in supportedCapabilities
}
