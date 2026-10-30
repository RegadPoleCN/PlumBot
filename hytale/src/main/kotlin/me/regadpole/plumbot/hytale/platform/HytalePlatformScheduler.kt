// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.hytale.platform

import com.hypixel.hytale.server.core.universe.Universe
import kotlinx.coroutines.*
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

/**
 * Hytale 侧的平台调度器。
 *
 * **同步语义**：Hytale 没有单一主线程，每个 World 是一条 tick 线程（World extends
 * TickingThread implements Executor）。runSync / runLater / runRepeating 一律经
 * [HytaleSyncDispatcher] 排到**默认世界的 tick 队列**，与 Bukkit 的 runTask 系列
 * 对应：不阻塞调用线程，任务在下一刻被消费。旧实现把 runSync 直接转发给 runAsync，
 * 调用方（控制台回显、渲染回调）的线程假设全部落空。
 *
 * 只有拿不到世界（启动早期 / 关服中 / 世界未加载）时才回退异步泳道，并**只告警一次**。
 */
class HytalePlatformScheduler(
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("PlumBot-Hytale-Scheduler")),
    private val warn: (String) -> Unit = {}
) : PlatformScheduler {

    init {
        // Hytale 侧此前**没有任何地方取消该作用域**：插件每次 disable/reload 都会漏掉
        // 一组调度线程与未完成 Job。Bukkit/Velocity 由宿主托管，不存在该问题。
        ShutdownRegistry.register(this) { shutdown() }
    }

    /** 回退告警只打一次：每帧回调都刷一行比丢一行更糟。 */
    private val fallbackWarned = AtomicBoolean(false)

    private val syncDispatcher = HytaleSyncDispatcher(
        enqueueWorldTask = { runnable ->
            val world = runCatching { Universe.get().defaultWorld }.getOrNull()
            if (world == null) {
                false
            } else {
                // 在**任务执行时**判定而不是入队时：入队线程可能是任意异步线程。
                runCatching {
                    world.execute(Runnable {
                        HytaleTickThreads.markCurrentThreadIsTickThread()
                        runnable.run()
                    })
                }.isSuccess
            }
        },
        runOnFallback = { runnable -> scope.launch { runnable.run() } },
        onFallbackUsed = {
            if (fallbackWarned.compareAndSet(false, true)) {
                warn(
                    "[PlumBot-Hytale] 当前没有可用的世界 tick 线程，同步调度已回退到异步泳道；" +
                        "这通常只发生在启动早期或关服阶段，世界可用后自动恢复。"
                )
            }
        }
    )

    /** 已排入世界队列的任务拿不到可取消句柄（宿主的 Executor 不返回 Future）。 */
    private fun queuedHandle(): PlatformTaskHandle = PlatformTaskHandle { false }

    override fun runSync(task: () -> Unit): PlatformTaskHandle {
        syncDispatcher.dispatch(task)
        return queuedHandle()
    }

    override fun runAsync(task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch { task() }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            // 延时在异步泳道上等，**到期后**再排进世界 tick 队列：世界线程上不睡觉。
            kotlinx.coroutines.delay(delay)
            syncDispatcher.dispatch(task)
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            kotlinx.coroutines.delay(delay)
            task()
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            kotlinx.coroutines.delay(initialDelay)
            while (isActive) {
                syncDispatcher.dispatch(task)
                kotlinx.coroutines.delay(period)
            }
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle {
        val job = scope.launch {
            kotlinx.coroutines.delay(initialDelay)
            while (isActive) {
                task()
                kotlinx.coroutines.delay(period)
            }
        }
        return PlatformTaskHandle { job.cancel(); true }
    }

    /** 取消调度作用域。由关闭钩子（[ShutdownRegistry]）在插件停用时调用；重复调用安全。 */
    fun shutdown() {
        ShutdownRegistry.unregister(this)
        scope.cancel()
    }
}
