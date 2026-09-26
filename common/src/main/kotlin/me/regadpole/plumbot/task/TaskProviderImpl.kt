package me.regadpole.plumbot.task

import kotlinx.coroutines.*
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@PublicApi
object TaskProviderImpl {
    private val mainScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val asyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun submit(task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            task.run()
        }
    }

    fun submitAsync(task: Runnable): Future<*> {
        return launchAsFuture(asyncScope) {
            task.run()
        }
    }

    fun submitLater(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            delay(delay)
            task.run()
        }
    }

    fun submitLaterAsync(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(asyncScope) {
            delay(delay)
            task.run()
        }
    }

    fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*> {
        return launchAsFuture(mainScope) {
            delay(delay)
            while (isActive) {
                task.run()
                delay(period)
            }
        }
    }

    fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*> {
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

    fun <T> launchFuture(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        asyncScope.launch {
            try {
                future.complete(block())
            } catch (e: Throwable) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private class JobFuture : CompletableFuture<Unit>(), PlatformTaskHandle {
        private var _job: Job? = null
        val job: Job get() = _job ?: throw IllegalStateException("Job is not initialized")

        fun initJob(job: Job) {
            this._job = job
        }

        override fun cancel(): Boolean {
            _job?.cancel()
            return super.cancel(false)
        }

        override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
            _job?.cancel()
            return super.cancel(mayInterruptIfRunning)
        }

        override fun isCancelled(): Boolean =
            _job?.isCancelled ?: super.isCancelled()

        override fun isDone(): Boolean =
            _job?.isCompleted ?: super.isDone()
    }
}
