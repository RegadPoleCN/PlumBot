package me.regadpole.plumbot.task

import kotlinx.coroutines.*
import java.util.concurrent.Future
import java.util.concurrent.CompletableFuture

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
        val future = CompletableFuture<Unit>()
        scope.launch {
            try {
                block()
                future.complete(Unit)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun shutdown() {
        mainScope.cancel()
        asyncScope.cancel()
    }
}
