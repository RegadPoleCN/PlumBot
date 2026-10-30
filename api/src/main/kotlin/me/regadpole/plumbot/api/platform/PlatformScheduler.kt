// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.StableApi
import kotlin.time.Duration

@StableApi
interface PlatformScheduler {

    /**
     * 在服务端主线程（游戏刻）立即执行。
     * Bukkit 侧确保在主线程执行；Velocity 没有独立主线程，交由代理调度器线程执行。
     */
    fun runSync(task: () -> Unit): PlatformTaskHandle

    /**
     * 在平台的异步工作线程池中立即执行。
     * 注意：Velocity 实现没有独立异步池，与 [runSync] 等价。
     */
    fun runAsync(task: () -> Unit): PlatformTaskHandle

    /**
     * 延时后在服务端主线程执行。
     * @param delay 延时时长（例如 5.seconds 或 100.ticks，内部按 50ms/tick 换算）
     */
    fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 延时后在异步工作线程池执行；Velocity 上与 [runLater] 等价。
     */
    fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 周期性任务（在服务端主线程执行）：首次等待 [initialDelay]，之后每 [period] 触发一次。
     */
    fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 周期性异步任务（在异步线程池执行；Velocity 上与 [runRepeating] 等价）。
     */
    fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle
}
