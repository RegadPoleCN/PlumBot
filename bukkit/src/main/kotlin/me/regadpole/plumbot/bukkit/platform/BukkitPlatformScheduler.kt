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
        val bukkitTask = Bukkit.getScheduler().runTask(plugin, Runnable { task() })
        return wrapTask(bukkitTask)
    }

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        val bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { task() })
        return wrapTask(bukkitTask)
    }

    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = delay.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable { task() }, delayTicks)
        return wrapTask(bukkitTask)
    }

    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = delay.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable { task() }, delayTicks)
        return wrapTask(bukkitTask)
    }

    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = initialDelay.toTicks()
        val periodTicks = period.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { task() }, delayTicks, periodTicks)
        return wrapTask(bukkitTask)
    }

    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val delayTicks = initialDelay.toTicks()
        val periodTicks = period.toTicks()
        val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { task() }, delayTicks, periodTicks)
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
