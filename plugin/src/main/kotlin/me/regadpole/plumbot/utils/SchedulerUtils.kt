package me.regadpole.plumbot.utils

import me.regadpole.plumbot.HytaleScheduler
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit

fun runTaskSync(runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.sync(runnable)
}

fun runTaskDelaySync(duration: Duration, runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.syncLater(runnable, duration.toLong(DurationUnit.SECONDS), TimeUnit.SECONDS)
}

fun runTaskRepeatSync(duration: Duration, runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.syncRepeating(runnable, duration.toLong(DurationUnit.SECONDS), duration.toLong(DurationUnit.SECONDS), TimeUnit.SECONDS)
}
fun runTaskAsync(runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.async(runnable)
}

fun runTaskDelayAsync(duration: Duration, runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.asyncLater(runnable, duration.toLong(DurationUnit.SECONDS), TimeUnit.SECONDS)
}

fun runTaskRepeatAsync(duration: Duration, runnable: Runnable): ScheduledFuture<*> {
    return HytaleScheduler.asyncRepeating(runnable, duration.toLong(DurationUnit.SECONDS), duration.toLong(DurationUnit.SECONDS), TimeUnit.SECONDS)
}