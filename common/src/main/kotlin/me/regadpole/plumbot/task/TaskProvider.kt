package me.regadpole.plumbot.task

import java.util.concurrent.Future

interface TaskProvider {
    // TODO: add cancel() method for every task submitted
    fun submit(task: Runnable): Future<*>

    fun submitAsync(task: Runnable): Future<*>

    fun submitLater(delay: Long, task: Runnable): Future<*>

    fun submitLaterAsync(delay: Long, task: Runnable): Future<*>

    fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*>

    fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*>
}