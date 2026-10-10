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

package me.regadpole.plumbot.render

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 渲染子系统的运行时指标。
 *
 * **为什么放在 `:render` 而不是 `:common` 的 `HealthCounters`**：`:render` 不依赖 `:common`
 * （模块边界由 `checkRenderBoundaries` 强制 —— 本对象声明为 `internal` 正是该检查的要求：
 * `:render` 的公开面只能经门户暴露）。
 */
internal object RenderMetrics {

    const val QUEUE_REJECTED = "render.queueRejected"
    const val FALLBACK = "render.fallback"
    const val EMITTED = "render.emitted"

    private val counters = ConcurrentHashMap<String, AtomicLong>()

    fun increment(key: String, delta: Long = 1L) {
        counters.computeIfAbsent(key) { AtomicLong() }.addAndGet(delta)
    }

    fun get(key: String): Long = counters[key]?.get() ?: 0L

    fun snapshot(): Map<String, Long> = counters.mapValues { it.value.get() }

    /** 重置（测试用）。 */
    fun reset() {
        counters.clear()
    }
}
