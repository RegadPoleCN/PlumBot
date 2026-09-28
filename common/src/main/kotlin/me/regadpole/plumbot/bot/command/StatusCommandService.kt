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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.platform.PlatformCapability
import me.regadpole.plumbot.config.Messages
import java.util.Locale

class StatusCommandService(private val service: BotCommandService) {

    fun showStatus(message: String, groupId: Long, userId: Long) {
        if (!service.context.hasCapability(PlatformCapability.SERVER_TPS_METRICS)) {
            service.sendRawMessage(groupId, Messages.statusUnavailable)
            return
        }

        val tpsArray = service.context.getRecentTps()
        val mspt = service.context.getMspt()

        val tps1m = tpsArray?.getOrNull(0)?.let { formatTps(it) } ?: "20.0"
        val tps5m = tpsArray?.getOrNull(1)?.let { formatTps(it) } ?: "20.0"
        val tps15m = tpsArray?.getOrNull(2)?.let { formatTps(it) } ?: "20.0"

        val msptStr = mspt?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "N/A"

        val runtime = Runtime.getRuntime()
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val totalMem = runtime.totalMemory() / (1024 * 1024)
        val freeMem = runtime.freeMemory() / (1024 * 1024)
        val usedMem = totalMem - freeMem

        val onlineCount = service.context.playerService.listPlayers().size

        val rendered = service.render(
            Messages.serverStatus,
            "%tps_1m%" to tps1m,
            "%tps_5m%" to tps5m,
            "%tps_15m%" to tps15m,
            "%mspt%" to msptStr,
            "%used_mem%" to usedMem.toString(),
            "%max_mem%" to maxMem.toString(),
            "%online%" to onlineCount.toString()
        )

        val usePic = service.context.config.getBoolean("feature", "status", "pic")
        service.sendRawMessage(groupId, rendered, usePic)
    }

    private fun formatTps(tps: Double): String {
        val clamped = if (tps > 20.0) 20.0 else tps
        return String.format(Locale.ROOT, "%.2f", clamped)
    }
}
