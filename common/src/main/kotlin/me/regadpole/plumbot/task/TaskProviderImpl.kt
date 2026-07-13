package me.regadpole.plumbot.task

import kotlinx.coroutines.*
import me.regadpole.plumbot.platform.PlatformTaskHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

object TaskProviderImpl : TaskProvider {
    private val mainScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val asyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun submit(task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            task.run()
        }
    }

    override fun submitAsync(task: Runnable): Future<*> {
        return launchAsFuture(asyncScope) {
            task.run()
        }
    }

    override fun submitLater(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            delay(delay)
            task.run()
        }
    }

    override fun submitLaterAsync(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(asyncScope) {
            delay(delay)
            task.run()
        }
    }

    override fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            delay(delay)
            while (isActive) {
                task.run()
                delay(period)
            }
        }
    }

    override fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*> {
        return launchAsFuture(asyncScope) {
            delay(delay)
            while (isActive) {
                task.run()
                delay(period)
            }
        }
    }

    private fun launchAsFuture(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Future<*> {
        val future = JobFuture()
        val job = scope.launch {
            try {
                block()
                future.complete(Unit)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        future.initJob(job)
        return future
    }

    fun shutdown() {
        mainScope.cancel()
        asyncScope.cancel()
    }

    private class JobFuture : CompletableFuture<Unit>(), PlatformTaskHandle {
        private var _job: Job? = null
        override val job: Job get() = _job ?: throw IllegalStateException("Job is not initialized")

        fun initJob(job: Job) {
            this._job = job
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            job.cancel()
            return super<CompletableFuture>.cancel(mayInterruptIfRunning)
        }

        override fun isCancelled(): Boolean = super.isCancelled() || job.isCancelled

        override fun isDone(): Boolean = super.isDone() || job.isCompleted
    }
}
