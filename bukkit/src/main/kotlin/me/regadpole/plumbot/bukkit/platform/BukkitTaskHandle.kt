package me.regadpole.plumbot.bukkit.platform

import kotlinx.coroutines.Job
import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture

class BukkitTaskHandle(
    val task: BukkitTask?,
    override val job: Job = Job(),
    val completionFuture: CompletableFuture<Void> = CompletableFuture(),
): PlatformTaskHandle {
    override fun cancel(): Boolean {
        if (task == null) return false
        if (task.isCancelled) return false
        task.cancel()
        completionFuture.cancel(false)
        return true
    }
}
