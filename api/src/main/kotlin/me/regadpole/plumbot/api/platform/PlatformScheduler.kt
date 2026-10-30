/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
