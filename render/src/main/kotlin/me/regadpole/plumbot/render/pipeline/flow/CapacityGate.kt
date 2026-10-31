// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.flow

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.pipeline.RenderMetrics
import me.regadpole.plumbot.render.support.RenderLimits

/**
 * **容量闸 + 同目标串行**：渲染入口的第一道（也是最后一道）资源保护。
 *
 * 两件事在这里合并，因为它们共享同一段临界区语义：
 * 1. 全局有界容量（并发 [RenderLimits.CONCURRENCY] + 排队 [RenderLimits.QUEUE_DEPTH]）：
 *    `tryAcquire` 失败即视为队列溢出，**立即降级纯文本**，不排队不阻塞；
 * 2. 同一目标（群 / 玩家）串行：保证出图顺序与消息顺序一致。
 */
internal class CapacityGate(private val context: PlatformContext) {

    /** 全局渲染容量 = 并发数 + 排队深度；`tryAcquire` 失败即视为队列溢出。 */
    private val capacity = Semaphore(RenderLimits.CONCURRENCY + RenderLimits.QUEUE_DEPTH)

    /** 同一目标串行渲染，保证出图顺序与消息顺序一致。 */
    private val targetLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * 按 `targetKey` 串行后进入容量闸。
     *
     * @param render 真正的渲染体（由 [me.regadpole.plumbot.render.pipeline.flow.RenderPipeline] 提供）。
     */
    suspend fun dispatch(
        route: RenderRoute,
        engine: String,
        request: RenderRequest,
        render: suspend () -> RenderResult
    ): RenderResult {
        val targetKey = request.targetKey
        if (targetKey == null) return renderWithCapacity(route, engine, render)

        val lock = targetLocks.computeIfAbsent(targetKey) { Mutex() }
        lock.lock()
        return try {
            renderWithCapacity(route, engine, render)
        } finally {
            lock.unlock()
            // 水位超限时只清理**空闲**条目：`clear()` 会把其它协程正在持有的 Mutex 一并
            // 丢弃，等待者与新调用者拿到的锁对象不同 ——"同一目标串行渲染"就此失效。
            // `isLocked` 为 false 但仍有人刚排队的极端时序下仍可能被移除，代价是那一瞬间
            // 的串行性，而不是把正在执行的渲染放跑。
            if (targetLocks.size > RenderLimits.MAX_TRACKED_TARGETS) {
                targetLocks.entries.removeIf { (key, mutex) -> key != targetKey && !mutex.isLocked }
            }
        }
    }

    /** 停服时丢弃目标锁表（此时不应再有渲染在途）。 */
    fun clear() {
        targetLocks.clear()
    }

    private suspend fun renderWithCapacity(
        route: RenderRoute,
        engine: String,
        render: suspend () -> RenderResult
    ): RenderResult {
        if (!capacity.tryAcquire()) {
            // 队列溢出会静默降级为纯文本：计入渲染指标（`:render` 不依赖 `:common`，指标自持）
            RenderMetrics.increment(RenderMetrics.QUEUE_REJECTED)
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 渲染容量已满（并发 ${RenderLimits.CONCURRENCY} + 排队 " +
                    "${RenderLimits.QUEUE_DEPTH}），路由 ${route.route} 立即降级为纯文本。"
            )
            return RenderResult.Unavailable
        }
        val result = try {
            render()
        } finally {
            capacity.release()
        }
        // 门户是渲染结果的唯一计数者（见 RenderHealth）：成功出图与"最终无图"在这里统一归类。
        // 注意 Unavailable 有两种来源：容量拒绝（前面已 return，不计入此处）与前置条件不足
        // （载荷超限 / 兜底引擎缺失），后者由渲染体内部按 failed 记录并带原因。
        when (result) {
            is RenderResult.Image -> RenderMetrics.increment(RenderMetrics.EMITTED)
            is RenderResult.Failed -> {
                RenderMetrics.increment(RenderMetrics.DEGRADED_FAILED)
                RenderMetrics.noteFailure(route.route, result.reason)
            }

            RenderResult.Unavailable -> Unit
        }
        return result
    }
}
