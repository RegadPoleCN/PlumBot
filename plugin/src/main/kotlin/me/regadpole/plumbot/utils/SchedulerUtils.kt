package me.regadpole.plumbot.utils

import com.velocitypowered.api.scheduler.ScheduledTask
import me.regadpole.plumbot.PlumBot
import kotlin.time.Duration
import kotlin.time.toJavaDuration

fun runTask(runnable: Runnable): ScheduledTask {
    return PlumBot.INSTANCE..buildTask(PlumBot.INSTANCE, runnable).schedule()
}

fun runTaskDelay(duration: Duration, runnable: Runnable): ScheduledTask {
    return PlumBot.INSTANCE.server.scheduler.buildTask(PlumBot.INSTANCE, runnable).delay(duration.toJavaDuration()).schedule()
}

fun runTaskRepeat(duration: Duration, runnable: Runnable): ScheduledTask {
    return PlumBot.INSTANCE.server.scheduler.buildTask(PlumBot.INSTANCE, runnable).repeat(duration.toJavaDuration()).schedule()
}