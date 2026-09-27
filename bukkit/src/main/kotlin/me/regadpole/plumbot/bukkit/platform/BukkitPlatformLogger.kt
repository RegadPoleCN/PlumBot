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

import me.regadpole.plumbot.DebugProvider
import me.regadpole.plumbot.internal.LogLevel
import java.util.logging.Logger

class BukkitPlatformLogger(
    private val logger: Logger,
    private val debugProvider: DebugProvider,
) {
    fun log(level: LogLevel, message: String) {
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
