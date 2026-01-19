/*
 * This file is part of PlumBot, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.regadpole.plumbot

import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.utils.warn
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate
import java.util.stream.Collectors

object HytaleScheduler {
    private const val PARALLELISM = 16
    private val list: MutableList<ScheduledFuture<*>> = mutableListOf()

    private val scheduler = ScheduledThreadPoolExecutor(1) { r: Runnable ->
        val thread = Executors.defaultThreadFactory().newThread(r)
        thread.name = "plumbot-scheduler"
        thread
    }
    private val worker: ForkJoinPool

    init {
        scheduler.removeOnCancelPolicy = true
        scheduler.executeExistingDelayedTasksAfterShutdownPolicy = false
        this.worker = ForkJoinPool(
            PARALLELISM,
            WorkerThreadFactory(),
            ExceptionHandler(),
            false
        )
    }

    fun sync(task: Runnable): ScheduledFuture<*> {
        val future = scheduler.schedule({
            worker.execute {
                Universe.get().defaultWorld!!.execute(task)
            }
        }, 0, TimeUnit.MILLISECONDS)
        //        return async(); // lucko: I think this is OK?
        list.add(future)
        return future
    }

    fun syncLater(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        val future = scheduler.schedule({
            worker.execute {
                Universe.get().defaultWorld!!.execute(task)
            }
        }, delay, unit)
        list.add(future)
        return future
    }

    fun syncRepeating(task: Runnable, delay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
        val future = scheduler.scheduleAtFixedRate({
            worker.execute {
                Universe.get().defaultWorld!!.execute(
                    task
                )
            }
        }, delay, period, unit)
        list.add(future)
        return future
    }

    fun async(task: Runnable): ScheduledFuture<*> {
//        return this.worker;
        val future = scheduler.schedule({ worker.execute(task) }, 0, TimeUnit.MILLISECONDS)
        list.add(future)
        return future
    }

    fun asyncLater(task: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
        val future = scheduler.schedule({ worker.execute(task) }, delay, unit)
        list.add(future)
        return future
    }

    fun asyncRepeating(task: Runnable, delay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
        val future = scheduler.scheduleAtFixedRate({ worker.execute(task) }, delay, period, unit)
        list.add(future)
        return future
    }

    fun shutdownScheduler() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                me.regadpole.plumbot.utils.error("Timed out waiting for the PlumBot scheduler to terminate")
                reportRunningTasks { thread: Thread -> thread.name == "plumbot-scheduler" }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun shutdownExecutor() {
        worker.shutdown()
        try {
            if (!worker.awaitTermination(1, TimeUnit.MINUTES)) {
                me.regadpole.plumbot.utils.error("Timed out waiting for the PlumBot worker thread pool to terminate")
                reportRunningTasks { thread: Thread -> thread.name.startsWith("plumbot-worker-") }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun cancelAllTasks() {
        list.forEach {
            it.cancel(false)
        }
    }

    private fun reportRunningTasks(predicate: Predicate<Thread>) {
        Thread.getAllStackTraces().forEach { (thread: Thread?, stack: Array<StackTraceElement?>?) ->
            if (predicate.test(thread)) {
                warn(
                    """Thread ${thread.name} is blocked, and may be the reason for the slow shutdown!
${
                        Arrays.stream(stack)
                            .map { el: StackTraceElement -> "  $el" }
                            .collect(Collectors.joining("\n"))
                    }"""
                )
            }
        }
    }

    private class WorkerThreadFactory : ForkJoinWorkerThreadFactory {
        override fun newThread(pool: ForkJoinPool): ForkJoinWorkerThread {
            val thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
            thread.isDaemon = true
            thread.name = "plumbot-worker-" + COUNT.getAndIncrement()
            return thread
        }

        companion object {
            private val COUNT = AtomicInteger(0)
        }
    }

    private class ExceptionHandler : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            warn("Thread " + t.name + " threw an uncaught exception")
            e.printStackTrace()
        }
    }
}