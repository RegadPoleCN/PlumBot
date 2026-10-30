// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * **指标缺失时的展示语义**：缺失**不得**显示成看起来正常的数值——TPS 读不到却显示 `20.0`，
 * 服主会看到一张"一切正常"的卡片，而指标根本没读到。
 */
class ServerMetricsTextTest {

    @Test
    fun `missing tps is reported as missing and never as a healthy value`() {
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(null as DoubleArray?, 0))
        assertNotEquals("20.0", ServerMetricsText.tps(null as DoubleArray?, 0), "缺失不得回落到完美 TPS（原缺陷）")

        // 长度为 1 的数组：后两个时间窗同样缺失
        val short = doubleArrayOf(19.5)
        assertEquals("19.50", ServerMetricsText.tps(short, 0))
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(short, 1))
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(short, 2))
    }

    @Test
    fun `non finite tps values are treated as missing`() {
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(Double.NaN))
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(Double.POSITIVE_INFINITY))
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.tps(null as Double?))
    }

    @Test
    fun `tps keeps two decimals and clamps above the physical maximum`() {
        assertEquals("19.98", ServerMetricsText.tps(19.984))
        assertEquals("20.00", ServerMetricsText.tps(21.5), "超过 20 只可能是读数异常，显示上钳制（保留原行为）")
        assertEquals("0.00", ServerMetricsText.tps(0.0))
    }

    @Test
    fun `mspt uses the same placeholder as tps so the card is consistent`() {
        assertEquals(ServerMetricsText.MISSING, ServerMetricsText.mspt(null))
        assertEquals("12.35", ServerMetricsText.mspt(12.345))
        assertEquals(
            ServerMetricsText.MISSING,
            ServerMetricsText.mspt(Double.NaN),
            "两种情况必须用同一个占位符 —— 此前 TPS 用 20.0、MSPT 用 N/A，语义不一致"
        )
    }

    @Test
    fun `the placeholder is a clearly non numeric token`() {
        assertTrue(
            ServerMetricsText.MISSING.any { !it.isDigit() && it != '.' },
            "占位符必须一眼看出不是数值：${ServerMetricsText.MISSING}"
        )
    }
}
