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
     * 在服务端主线程（游戏主刻）立即执行任务。
     * 单服平台 (Bukkit/Fabric) 确保在主线程执行；代理端平台 (Velocity) 交由默认执行线程处理。
     */
    fun runSync(task: () -> Unit): PlatformTaskHandle

    /**
     * 在平台的异步工作线程池中立即执行任务。
     */
    fun runAsync(task: () -> Unit): PlatformTaskHandle

    /**
     * 延时指定时长后，在服务端主线程执行。
     *
     * @param delay 延时时长（例如 5.seconds 或 100.ticks）
     * @param task 执行内容
     */
    fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 延时指定时长后，在异步工作线程池执行。
     */
    fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 定时周期性任务（在服务端主线程执行）。
     *
     * @param initialDelay 首次执行前的延时
     * @param period 两次执行之间的周期时长
     */
    fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle

    /**
     * 定时周期性异步任务（在异步线程池执行）。
     */
    fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle
}
