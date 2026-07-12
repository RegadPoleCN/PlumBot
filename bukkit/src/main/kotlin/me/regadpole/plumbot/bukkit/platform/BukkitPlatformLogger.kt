package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.platform.PlatformLogger
import java.util.logging.Logger

class BukkitPlatformLogger(
    private val logger: Logger,
    private val debugProvider: DebugProvider,
): PlatformLogger {
    override fun log(level: LogLevel, message: String) {
        when (level) {
            LogLevel.TRACE -> {
                logger.finest(message)
                debugProvider.log(message)
            }
            LogLevel.DEBUG -> {
                logger.fine(message)
                debugProvider.log(message)
            }
            LogLevel.INFO -> logger.info(message)
            LogLevel.WARN -> logger.warning(message)
            LogLevel.ERROR -> logger.severe(message)
            LogLevel.FATAL -> logger.severe(message)
        }
    }
}
