package me.regadpole.plumbot.task

import java.util.concurrent.Future

/**
 * 统一的任务调度接口，用于在不同平台（Common / Bukkit / Adapter）下提交一次性、延迟与定时任务。
 *
 * 取消语义：调用方通过 [submit] / [submitAsync] / [submitLater] / [submitLaterAsync] /
 * [submitTimer] / [submitTimerAsync] 返回的 [Future] 进行取消（例如 `future.cancel(true)`）。
 * [TaskProvider] 本身不提供单独的 cancel 入口，所有任务生命周期都通过该 [Future] 暴露。
 *
 * 实现需保证返回的 [Future] 与底层协程 [kotlinx.coroutines.Job] 的状态保持一致：
 * - 当协程被取消时，[Future.isCancelled] 应返回 `true`。
 * - 当协程执行完成（正常结束、异常或取消）时，[Future.isDone] 应返回 `true`。
 */
interface TaskProvider {
    fun submit(task: Runnable): Future<*>

    fun submitAsync(task: Runnable): Future<*>

    fun submitLater(delay: Long, task: Runnable): Future<*>

    fun submitLaterAsync(delay: Long, task: Runnable): Future<*>

    fun submitTimer(delay: Long, period: Long, task: Runnable): Future<*>

    fun submitTimerAsync(delay: Long, period: Long, task: Runnable): Future<*>
}