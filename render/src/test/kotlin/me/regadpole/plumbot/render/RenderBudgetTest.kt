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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
