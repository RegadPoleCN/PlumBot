package me.regadpole.plumbot.platform

import kotlinx.coroutines.Job
import me.regadpole.plumbot.api.StableApi

@StableApi
interface PlatformTaskHandle {
    val job: Job
    fun cancel(): Boolean {
        if (!job.isActive) return false
        job.cancel()
        return true
    }
}
