package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.platform.PlatformScheduler
import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin

class BukkitPlatformScheduler(
    private val plugin: Plugin,
): PlatformScheduler {
    override fun run(task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTask(plugin, task))
    }

    override fun runAsync(task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTaskAsynchronously(plugin, task))
    }

    override fun runLater(delay: Long, task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTaskLater(plugin, task, delay))
    }

    override fun runLaterAsync(delay: Long, task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay))
    }

    override fun runTimer(delay: Long, period: Long, task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period))
    }

    override fun runTimerAsync(delay: Long, period: Long, task: Runnable): PlatformTaskHandle {
        return BukkitTaskHandle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period))
    }
}
