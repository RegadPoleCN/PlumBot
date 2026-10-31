// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap
import me.regadpole.plumbot.render.RenderHealth

/**
 * 渲染子系统的运行时指标（`:render` 不依赖 `:common`，所以不能放进 HealthCounters；
 * 本对象声明为 internal，`:render` 的公开面只能经门户暴露）。
 *
 * 口径见 [RenderHealth]：本对象是**唯一**记录「渲染结果」的地方，业务层不再重复计数。
 */
internal object RenderMetrics {

    const val QUEUE_REJECTED = "render.queueRejected"
    const val FALLBACK = "render.fallback"
    const val FALLBACK_AWT = "render.fallbackAwt"
    const val DEGRADED_FAILED = "render.failed"
    const val ENGINE_UNAVAILABLE = "render.engineUnavailable"
    const val EMITTED = "render.emitted"

    private const val LAST_FAILURE_MAX_CHARS = 160

    private val counters = ConcurrentHashMap<String, AtomicLong>()

    @Volatile
    private var lastFailure: String? = null

    fun increment(key: String, delta: Long = 1L) {
        counters.computeIfAbsent(key) { AtomicLong() }.addAndGet(delta)
    }

    fun get(key: String): Long = counters[key]?.get() ?: 0L

    fun snapshot(): Map<String, Long> = counters.mapValues { it.value.get() }

    /** 记录最近一次失败摘要：健康行是单行文本，只保留最后一条，历史失败由服务端日志承担。 */
    fun noteFailure(route: String, reason: String) {
        val text = (route + ": " + reason).replace('\n', ' ').replace('\r', ' ')
        lastFailure = if (text.length <= LAST_FAILURE_MAX_CHARS) text
        else text.take(LAST_FAILURE_MAX_CHARS) + "…"
    }

    /** 分类快照；字段口径见 [RenderHealth]。 */
    fun health(): RenderHealth = RenderHealth(
        emitted = get(EMITTED),
        degraded = get(FALLBACK),
        fallbackAwt = get(FALLBACK_AWT),
        failed = get(DEGRADED_FAILED),
        queueRejected = get(QUEUE_REJECTED),
        engineUnavailable = get(ENGINE_UNAVAILABLE),
        lastFailure = lastFailure
    )

    /** 清零（/plumbot render reset 与测试）。 */
    fun reset() {
        counters.clear()
        lastFailure = null
    }
}
