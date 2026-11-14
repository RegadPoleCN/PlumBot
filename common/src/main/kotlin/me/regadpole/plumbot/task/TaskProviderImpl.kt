// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.task

import kotlinx.coroutines.*
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.time.Duration.Companion.milliseconds

@PublicApi
object TaskProviderImpl {
    private val scopeLock = Any()

    // @Volatile + 取消后重建：平台"禁用→再次启用"时 shutdown() 取消过的作用域必须能恢复，
    // 否则重新启用后 submit* 静默不入队、等待结果的调用永久挂起。
    @Volatile
    private var mainScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var asyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun readyMainScope(): CoroutineScope {
        val current = mainScope
        if (current.isActive) return current
        synchronized(scopeLock) {
            if (!mainScope.isActive) mainScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            return mainScope
        }
    }

    private fun readyAsyncScope(): CoroutineScope {
        val current = asyncScope
        if (current.isActive) return current
        synchronized(scopeLock) {
            if (!asyncScope.isActive) asyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            return asyncScope
        }
    }

    fun submit(task: Runnable): Future<*> {
        return launchAsFuture(readyMainScope()) {
            task.run()
        }
    }

    fun submitAsync(task: Runnable): Future<*> {
        return launchAsFuture(readyAsyncScope()) {
            task.run()
        }
    }

    fun submitLater(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(readyMainScope()) {
            delay(delay.milliseconds)
            task.run()
        }
    }

    fun submitLaterAsync(delay: Long, task: Runnable): Future<*> {
        return launchAsFuture(readyAsyncScope()) {
            delay(delay.milliseconds)
            task.run()
        }
    }

    fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*> {
        return launchAsFuture(readyMainScope()) {
            delay(delay.milliseconds)
            while (isActive) {
                task.run()
                delay(period.milliseconds)
            }
        }
    }

    fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*> {
        return launchAsFuture(readyAsyncScope()) {
            delay(delay.milliseconds)
            while (isActive) {
                task.run()
                delay(period.milliseconds)
            }
        }
    }

    private fun launchAsFuture(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit): Future<*> {
        val future = JobFuture()
        val job = scope.launch {
            try {
                block()
                future.complete(Unit)
            } catch (t: Throwable) {
                // 捕获 Throwable：Error（NoClassDefFoundError 等）若漏掉，Future 永不完成，
                // get() 调用方永久挂起；CancellationException 以取消语义传播，符合 Future 约定。
                future.completeExceptionally(t)
            }
        }
        future.initJob(job)
        return future
    }

    fun shutdown() {
        synchronized(scopeLock) {
            mainScope.cancel()
            asyncScope.cancel()
        }
    }

    fun <T> launchFuture(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        readyAsyncScope().launch {
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
