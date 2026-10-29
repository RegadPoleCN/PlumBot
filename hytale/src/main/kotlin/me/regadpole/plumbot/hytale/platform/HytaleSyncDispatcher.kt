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

package me.regadpole.plumbot.hytale.platform

/**
 * [HytalePlatformScheduler.runSync] 的派发器。
 *
 * Hytale 没有 Bukkit 那种单一主线程：每个 World 自己就是一条 tick 线程
 * （World extends TickingThread implements Executor），"主线程同步执行"的真实含义是
 * "排到默认世界的 tick 队列" —— 不阻塞调用线程、下一刻运行、可从任意线程调用。
 *
 * 只有拿不到世界（启动早期 / 关服中 / 世界未加载）时才回退异步泳道，任务因此脱离
 * tick 线程，必须经 onFallbackUsed 留痕。
 *
 * 不引用 Hytale 类型（世界经 enqueueWorldTask 注入），以便在 :hytale:test 断言未内联执行。
 */
internal class HytaleSyncDispatcher(
    private val enqueueWorldTask: (Runnable) -> Boolean,
    private val runOnFallback: (Runnable) -> Unit,
    private val onFallbackUsed: () -> Unit = {}
) {

    /** @return true = 已排入世界 tick 队列；false = 无可用世界线程、已退回异步。 */
    fun dispatch(task: () -> Unit): Boolean {
        val runnable = Runnable { task() }
        // 世界 execute 抛异常等价于"这条线程用不了"：宁可退回异步，也不能吞掉任务。
        val queued = runCatching { enqueueWorldTask(runnable) }.getOrDefault(false)
        if (queued) return true
        onFallbackUsed()
        runOnFallback(runnable)
        return false
    }
}
