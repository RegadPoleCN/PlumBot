// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.pipeline.FailureCooldown

/**
 * 失败熔断测试：没有熔断时，一个查不到皮肤的玩家会在每次列表刷新时跑满整条漏斗（最坏 3 秒），
 * 列表里十几个这样的玩家就足以拖垮单次出图。
 */
class FailureCooldownTest {

    @Test
    fun `未记录失败时不处于冷却`() {
        val cooldown = FailureCooldown(ttlMillis = 60_000) { 0L }
        assertFalse(cooldown.isCooling("Steve"))
    }

    @Test
    fun `记录失败后进入冷却`() {
        var now = 1_000L
        val cooldown = FailureCooldown(ttlMillis = 60_000) { now }
        cooldown.recordFailure("Steve")

        assertTrue(cooldown.isCooling("Steve"))
        // 其他玩家不受影响
        assertFalse(cooldown.isCooling("Alex"))

        now += 59_999
        assertTrue(cooldown.isCooling("Steve"), "冷却期内持续熔断")

        now += 1
        assertFalse(cooldown.isCooling("Steve"), "到期后允许重试一次")
    }

    @Test
    fun `过期条目在查询时被清理`() {
        var now = 0L
        val cooldown = FailureCooldown(ttlMillis = 100) { now }
        cooldown.recordFailure("Steve")
        assertEquals(1, cooldown.size())

        now += 200
        cooldown.isCooling("Steve")
        assertEquals(0, cooldown.size(), "过期项不应长期占用内存")
    }

    @Test
    fun `成功时清除冷却`() {
        val cooldown = FailureCooldown(ttlMillis = 60_000) { 0L }
        cooldown.recordFailure("Steve")
        assertTrue(cooldown.isCooling("Steve"))

        cooldown.clear("Steve")
        assertFalse(cooldown.isCooling("Steve"), "来源恢复后应立即可重试")
    }

    @Test
    fun `ttl 为 0 表示禁用熔断`() {
        val cooldown = FailureCooldown(ttlMillis = 0) { 0L }
        cooldown.recordFailure("Steve")
        assertFalse(cooldown.isCooling("Steve"), "禁用时不应拦截")
        assertEquals(0, cooldown.size())
    }

    @Test
    fun `大量键不会无限增长`() {
        var now = 0L
        val cooldown = FailureCooldown(ttlMillis = 60_000) { now }
        repeat(10_000) { cooldown.recordFailure("player$it") }
        assertTrue(cooldown.size() <= 4096, "应受上限约束，实际 ${cooldown.size()}")
    }
}
