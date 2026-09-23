package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.platform.PlatformScheduler
import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture

class BukkitPlatformScheduler(
    private val plugin: Plugin,
): PlatformScheduler {

    private fun wrapRunnable(task: Runnable, future: CompletableFuture<Void>): Runnable {
        return Runnable {
            try {
                task.run()
                future.complete(null)
            } catch (e: Throwable) {
                future.completeExceptionally(e)
                throw e
            }
        }
    }

    override fun run(task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTask(plugin, wrapRunnable(task, future))
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }

    override fun runAsync(task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTaskAsynchronously(plugin, wrapRunnable(task, future))
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }

    override fun runLater(delay: Long, task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, wrapRunnable(task, future), delay)
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }

    override fun runLaterAsync(delay: Long, task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, wrapRunnable(task, future), delay)
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }

    override fun runTimer(delay: Long, period: Long, task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            try {
                task.run()
            } catch (e: Throwable) {
                future.completeExceptionally(e)
                throw e
            }
        }, delay, period)
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }

    override fun runTimerAsync(delay: Long, period: Long, task: Runnable): PlatformTaskHandle {
        val future = CompletableFuture<Void>()
        val bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
            try {
                task.run()
            } catch (e: Throwable) {
                future.completeExceptionally(e)
                throw e
            }
        }, delay, period)
        return BukkitTaskHandle(bukkitTask, completionFuture = future)
    }
}
