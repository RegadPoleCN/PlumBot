package me.regadpole.plumbot.platform

import me.regadpole.plumbot.api.StableApi

@StableApi
interface PlatformScheduler {
    fun run(task: Runnable): PlatformTaskHandle

    fun runAsync(task: Runnable): PlatformTaskHandle

    fun runLater(delay: Long, task: Runnable): PlatformTaskHandle

    fun runLaterAsync(delay: Long, task: Runnable): PlatformTaskHandle

    fun runTimer(delay: Long, period: Long, task: Runnable): PlatformTaskHandle

    fun runTimerAsync(delay: Long, period: Long, task: Runnable): PlatformTaskHandle
}
