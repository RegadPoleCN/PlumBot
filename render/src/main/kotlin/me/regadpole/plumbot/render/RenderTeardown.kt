// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock

/**
 * 渲染资源的**回收线程**：把引擎关闭从调用线程上摘下来。
 *
 * Playwright 的 `Browser.close()` / `PlaywrightImpl.close()` 会同步等待 node 驱动进程退出
 * （实测单次 2~3s），而门户 `shutdown()` 通常从**服务端主线程**调用（`/plumbot reload`、
 * 插件禁用、服关闭），这段等待会直接变成一次 MSPT 尖峰（实测单 tick 1817ms）。
 *
 * 约定：
 * - [submit] **只入队，永不阻塞调用线程**，任务在唯一的守护线程上串行执行；
 * - 任务内的异常被吞掉并交给 `onFailure`，绝不影响后续回收（旧引擎关不掉不该拖垮新引擎）；
 * - 线程是 daemon：进程退出时不会因还有未完成的 close 而挂住 JVM。
 */
internal object RenderTeardown {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlumBot-Render-Reaper").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val lock = ReentrantLock()
    private val idle = lock.newCondition()
    private var pending = 0

    /**
     * 提交一段回收逻辑。
     *
     * @param onFailure 回收失败时的日志出口（通常是 `context.log(WARN, ...)`），它自身抛异常也会被吞掉。
     */
    fun submit(onFailure: (Throwable) -> Unit, block: () -> Unit) {
        lock.lock()
        try {
            pending++
        } finally {
            lock.unlock()
        }
        try {
            executor.execute {
                try {
                    block()
                } catch (t: Throwable) {
                    runCatching { onFailure(t) }
                } finally {
                    finished()
                }
            }
        } catch (t: Throwable) {
            // 线程池已关闭（JVM 退出中）：回收已无意义，记录后丢弃。
            finished()
            runCatching { onFailure(t) }
        }
    }

    private fun finished() {
        lock.lock()
        try {
            pending--
            if (pending <= 0) idle.signalAll()
        } finally {
            lock.unlock()
        }
    }

    /**
     * 等待队列排空（停服路径 `shutdownAndAwait` 会调用它）。
     *
     * 引擎关闭搬到守护线程后，`shutdown()` 返回时 node 驱动可能仍在退出。若调用方是插件
     * `disable()`，平台随后就会关闭插件类加载器，回收线程再去调用 `PlaywrightImpl.close()`
     * 立刻抛 "The plugin classloader for PlumBot has thrown a zip file error."（线上实测）。
     *
     * @return 在超时前排空返回 true。
     */
    fun awaitIdle(timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        lock.lock()
        try {
            while (pending > 0) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return false
                idle.await(remaining, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            return true
        } finally {
            lock.unlock()
        }
    }
}
