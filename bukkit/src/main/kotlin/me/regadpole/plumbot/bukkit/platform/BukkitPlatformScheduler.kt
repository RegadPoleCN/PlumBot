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

import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.toTicks
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import kotlin.time.Duration

class BukkitPlatformScheduler(
    private val plugin: Plugin,
) : PlatformScheduler {

    override fun runSync(task: () -> Unit): PlatformTaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTask(plugin) { task() }
        return wrapTask(bukkitTask)
    }

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin) { task() }
        return wrapTask(bukkitTask)
    }

    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = delay.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, { task() }, delayTicks)
        return wrapTask(bukkitTask)
    }

    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = delay.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, { task() }, delayTicks)
        return wrapTask(bukkitTask)
    }

    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = initialDelay.toTicks()
        val periodTicks = period.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, { task() }, delayTicks, periodTicks)
        return wrapTask(bukkitTask)
    }

    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = initialDelay.toTicks()
        val periodTicks = period.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, { task() }, delayTicks, periodTicks)
        return wrapTask(bukkitTask)
    }

    private fun wrapTask(task: BukkitTask?): PlatformTaskHandle {
        return PlatformTaskHandle {
            if (task == null || task.isCancelled) false
            else {
                task.cancel()
                true
            }
        }
    }
}
