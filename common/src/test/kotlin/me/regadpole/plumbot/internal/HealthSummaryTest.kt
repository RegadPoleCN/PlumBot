// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import me.regadpole.plumbot.render.RenderHealth
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 健康摘要行的**排版契约**：零值分类不出现、非零分类按固定顺序出现、最近失败会截断。
 *
 * 只测排版，不测分类口径（口径由 [RenderHealth] / [HealthCounters] 的调用方决定）。
 */
class HealthSummaryTest {

    private fun line(render: RenderHealth = RenderHealth.EMPTY): String = HealthSummary.line(
        render = render,
        disabled = 0,
        warmUpFailures = 0,
        sendFailed = 0,
        filterHits = 0,
        filterReloadFailures = 0,
        botStarted = 1,
        botUnloaded = 0,
        botState = "RUNNING",
        reconnects = 0,
        dbInitFailures = 0
    )

    @Test
    fun `零值分类不出现在摘要里`() {
        val text = line()

        assertTrue(text.startsWith("渲染 出图 0 / 降级 0 / 失败 0"), "实际: $text")
        assertFalse(text.contains("AWT"), "AWT 兜底为 0 时不该出现，否则每行都变长: $text")
        assertFalse(text.contains("队列拒绝"), "实际: $text")
        assertFalse(text.contains("引擎未就绪"), "实际: $text")
        assertFalse(text.contains("发图失败"), "实际: $text")
        assertFalse(text.contains("最近失败"), "从未失败过时不该出现该字段，否则会误导成故障: $text")
        assertTrue(
            text.endsWith("（出图含兜底图 · 截至上一次出图，本次不计入）"),
            "必须声明口径：出图含兜底图、且不含本次渲染，否则会被当成实时值: $text"
        )
    }

    @Test
    fun `非零分类按 出图 降级 失败 的顺序出现`() {
        val text = line(
            RenderHealth(
                emitted = 7,
                degraded = 3,
                fallbackAwt = 2,
                failed = 1,
                queueRejected = 4,
                engineUnavailable = 5,
                lastFailure = "playwright: 超时"
            )
        )

        listOf(
            "渲染 出图 7 / 降级 3（AWT 兜底 2） / 失败 1",
            "队列拒绝 4",
            "引擎未就绪 5",
            "最近失败 playwright: 超时"
        ).forEach { fragment ->
            assertTrue(text.contains(fragment), "缺少片段 [$fragment]，实际: $text")
        }
    }

    @Test
    fun `最近失败过长时截断到展示上限`() {
        val longReason = "x".repeat(60)
        val text = line(RenderHealth(lastFailure = longReason))

        assertTrue(text.contains("x".repeat(48) + "…"), "应截断到 48 字符并加省略号: $text")
        assertFalse(text.contains(longReason), "整段原因不能进卡片 footer（会折行穿版）: $text")
    }
}
