package me.regadpole.plumbot.bukkit.platform

import kotlinx.coroutines.Job
import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.scheduler.BukkitTask

class BukkitTaskHandle(
    val task: BukkitTask?,
    override val job: Job = Job(),
): PlatformTaskHandle {
    override fun cancel(): Boolean {
        if (task == null) return false
        if (task.isCancelled) return false
        task.cancel()
        return true
    }
}
