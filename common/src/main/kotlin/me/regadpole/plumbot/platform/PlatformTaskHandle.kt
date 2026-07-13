package me.regadpole.plumbot.platform

import kotlinx.coroutines.Job

interface PlatformTaskHandle {
    val job: Job
    fun cancel(): Boolean {
        if (!job.isActive) return false
        job.cancel()
        return true
    }
}
