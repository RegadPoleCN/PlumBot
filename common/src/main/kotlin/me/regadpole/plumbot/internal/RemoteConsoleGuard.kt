// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * 远程控制台的**执行闸门**：只做限流与并发控制（不做指令黑名单）。
 *
 * 远程控制台是"以服务器身份执行任意指令"的能力。管理员账号被盗、或群里被诱导刷指令时，没有
 * 限流就能**瞬间打满服务端**（连续 `save-all`、`reload`）；只看"是不是管理员"不够 —— 管理员
 * 也可能误操作或被冒充。
 *
 * 两道闸门：**令牌桶（按 QQ）**，容量 = 每分钟允许次数、按时间匀速补充，允许短时突发但持续
 * 刷屏会被拦（比"固定间隔"更符合人工操作习惯）；**全局并发上限**，同时在跑的指令数封顶，避免
 * 一串阻塞型指令把调度线程/输出缓冲吃光。
 *
 * 时间源可注入，因此测试不需要真的等待。
 */
internal class RemoteConsoleGuard(
    private val perMinute: Int,
    private val maxConcurrent: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    sealed interface Decision {
        /** 允许执行；调用方**必须**在执行结束后调用 [release]。 */
        data object Allowed : Decision

        /** 该 QQ 触发频率限制。 */
        data object RateLimited : Decision

        /** 全局并发已达上限。 */
        data object TooManyConcurrent : Decision
    }

    private class Bucket(var tokens: Double, var lastRefillMs: Long)

    private val buckets = ConcurrentHashMap<Long, Bucket>()
    private val inFlight = AtomicInteger(0)

    /** 最多跟踪的 QQ 数：超过后清理令牌已满（即长时间未活动）的记录，避免长期运行后无界增长。 */
    private val maxTrackedUsers = 1024

    fun tryAcquire(userId: Long): Decision {
        if (maxConcurrent > 0 && inFlight.get() >= maxConcurrent) return Decision.TooManyConcurrent
        if (perMinute <= 0) {
            inFlight.incrementAndGet()
            return Decision.Allowed
        }

        val now = clock()
        val bucket = buckets.computeIfAbsent(userId) { Bucket(perMinute.toDouble(), now) }
        synchronized(bucket) {
            val elapsed = (now - bucket.lastRefillMs).coerceAtLeast(0L)
            bucket.tokens = min(perMinute.toDouble(), bucket.tokens + elapsed * perMinute / 60_000.0)
            bucket.lastRefillMs = now

            if (bucket.tokens < 1.0) return Decision.RateLimited
            bucket.tokens -= 1.0
        }

        pruneIfNeeded()
        inFlight.incrementAndGet()
        return Decision.Allowed
    }

    /** 执行结束（含异常）后必须调用，否则并发计数只增不减，最终会把自己彻底锁死。 */
    fun release() {
        inFlight.updateAndGet { current -> if (current > 0) current - 1 else 0 }
    }

    /** 当前在途数量（供测试与状态展示）。 */
    fun inFlightCount(): Int = inFlight.get()

    private fun pruneIfNeeded() {
        if (buckets.size <= maxTrackedUsers) return
        // 只清理"桶已满"的条目（等价于长时间未活动的用户）
        buckets.entries.removeIf { (_, bucket) -> !bucket.tokens.isFullEnough(perMinute) }
    }

    private fun Double.isFullEnough(capacity: Int): Boolean = this >= capacity - 0.5
}
