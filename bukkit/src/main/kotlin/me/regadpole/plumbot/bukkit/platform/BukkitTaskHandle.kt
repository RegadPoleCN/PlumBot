package me.regadpole.plumbot.bukkit.platform

import me.regadpole.plumbot.platform.PlatformTaskHandle
import org.bukkit.scheduler.BukkitTask

class BukkitTaskHandle(
    private val task: BukkitTask?,
): PlatformTaskHandle {
    override fun cancel(): Boolean {
        task?.cancel()
        return task != null
    }
}
