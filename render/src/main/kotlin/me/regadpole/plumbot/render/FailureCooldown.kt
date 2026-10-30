// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.util.concurrent.ConcurrentHashMap

/**
 * 头像漏斗的**失败熔断**：失败后一段时间内不再重试。没有它时，查不到皮肤的玩家会在**每次**
 * 列表刷新时把整条漏斗跑满（逐级 800ms，最坏 3 秒），列表里十几个这样的玩家就足以拖垮单次出图。
 * 记的是**失败**而不是成功：成功已有磁盘缓存，失败若不留痕就会无限重试。
 * 冷却期内直接返回 Miss（调用方立刻降级到 QQ 头像），到点后允许重试一次。
 *
 * 纯逻辑 + 注入时钟，便于单测；线程安全（漏斗在 IO 线程池上并发执行）。
 */
internal class FailureCooldown(
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val failures = ConcurrentHashMap<String, Long>()

    /** 处于冷却期则返回 true（调用方应直接降级，不再发起外部请求）。 */
    fun isCooling(key: String): Boolean {
        val until = failures[key] ?: return false
        if (clock() >= until) {
            failures.remove(key)
            return false
        }
        return true
    }

    /** 记录一次失败，进入冷却。 */
    fun recordFailure(key: String) {
        if (ttlMillis <= 0) return
        // 表无限增长会缓慢泄漏；超过阈值时清掉已过期项
        if (failures.size >= MAX_ENTRIES) purge()
        failures[key] = clock() + ttlMillis
    }

    /** 成功时清除冷却（该来源已恢复）。 */
    fun clear(key: String) {
        failures.remove(key)
    }

    fun size(): Int = failures.size

    private fun purge() {
        val now = clock()
        failures.entries.removeIf { it.value <= now }
        // 极端情况（大量键同时未过期）下直接清空：宁可多试几次，也不无限增长
        if (failures.size >= MAX_ENTRIES) failures.clear()
    }

    private companion object {
        const val MAX_ENTRIES = 4096
    }
}
