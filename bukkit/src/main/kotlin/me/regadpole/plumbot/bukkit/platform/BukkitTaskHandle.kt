package me.regadpole.plumbot.bukkit.platform

import kotlinx.coroutines.SupervisorJob
import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.scheduler.BukkitTask

class BukkitTaskHandle(
    private val task: BukkitTask?,
): PlatformTaskHandle {
    override val job = SupervisorJob()

    override fun cancel(): Boolean {
        task?.cancel()
        if (!job.isActive) return false
        job.cancel()
        return true
    }
}
