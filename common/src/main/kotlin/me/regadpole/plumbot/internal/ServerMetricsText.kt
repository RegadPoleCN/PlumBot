// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import java.util.Locale

/**
 * 服务端指标的**展示文本**。
 *
 * 指标拿不到时怎么显示是**语义**问题而非格式问题：把读不到的 TPS 显示成字面量 **"20.0"**
 * （"完美 TPS"）会让代理端服主看到一张"一切正常"的卡片，而真实情况是这个指标根本没读到；
 * MSPT 那边又用 "N/A"，两者语义还不一致。
 *
 * 现在的规则：**拿不到就明说拿不到**（统一占位符），绝不回落成看起来正常的数值。
 */
object ServerMetricsText {

    /** 指标不可用时的统一占位符。 */
    const val MISSING = "N/A"

    /** TPS 的物理上限：超过它只可能是读数异常，显示上钳制（保留原行为）。 */
    private const val MAX_TPS = 20.0

    /**
     * @param values 平台返回的 TPS 数组（`[1m, 5m, 15m]`）；为 null 或长度不足时该窗口视为不可用
     */
    fun tps(values: DoubleArray?, window: Int): String =
        values?.getOrNull(window)?.let { tps(it) } ?: MISSING

    /** 单个 TPS 值的文本；null 视为不可用。 */
    fun tps(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return MISSING
        val clamped = if (value > MAX_TPS) MAX_TPS else value
        return String.format(Locale.ROOT, "%.2f", clamped)
    }

    /** MSPT 的文本（毫秒，两位小数）；null/非有限值视为不可用。 */
    fun mspt(value: Double?): String {
        if (value == null || value.isNaN() || value.isInfinite()) return MISSING
        return String.format(Locale.ROOT, "%.2f", value)
    }
}
