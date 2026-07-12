package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.config.YamlConfigurator
import java.nio.file.Path

interface PlatformContext {
    val config: YamlConfigurator
    val datasource: YamlConfigurator
    val dataDirectory: Path
    val logger: PlatformLogger
    val scheduler: PlatformScheduler
    val playerService: PlatformPlayerService
    val messenger: PlatformMessenger
    val platformType: PlatformType

    fun isPluginAvailable(name: String): Boolean
}
