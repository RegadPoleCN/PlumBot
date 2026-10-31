// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.pipeline.RenderBudget

/**
 * `options.timeoutMs` 是**整次渲染**预算，各阶段只能花"剩余额度"。
 * 用可控时钟模拟"阶段耗时累计"，因此不需要真实浏览器即可断言预算语义。
 */
class RenderBudgetTest {

    @Test
    fun `stages share one budget instead of each getting the full timeout`() {
        var now = 0L
        val budget = RenderBudget(timeoutMs = 1_000, clock = { now })

        assertEquals(1_000L, budget.remainingMs(), "起始时剩余等于全部预算")

        now += 400 // 第一阶段（等页面池）用掉 400ms
        assertEquals(600L, budget.remainingMs())

        now += 400 // 第二阶段（导航）用掉 400ms
        assertEquals(200L, budget.remainingMs(), "第三阶段只剩 200ms —— 而不是重新拿满 1000ms")

        now += 400 // 第三阶段超支
        assertTrue(budget.exhausted(), "累计超时后必须判为已耗尽")
        assertEquals(1L, budget.remainingMs(), "剩余额度下限为 1ms（0 会让 Playwright 立刻抛异常）")
    }

    @Test
    fun `a single stage cannot consume more than the whole budget`() {
        var now = 0L
        val budget = RenderBudget(timeoutMs = 500, clock = { now })

        now += 10_000
        assertTrue(budget.exhausted())
        assertFalse(budget.remainingMs() > 1L, "耗尽后不得再给出正数余额")
    }

    @Test
    fun `zero or negative timeout is clamped to a usable budget`() {
        var now = 0L
        val zero = RenderBudget(timeoutMs = 0, clock = { now })
        assertTrue(zero.remainingMs() >= 1L, "0 超时也要给出可用额度，而不是立刻判定耗尽")
        assertFalse(zero.exhausted())
    }
}
