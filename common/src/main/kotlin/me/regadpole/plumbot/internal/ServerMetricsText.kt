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

package me.regadpole.plumbot.internal

import java.util.Locale

/**
 * 服务端指标的**展示文本**（R31）。
 *
 * ## 为什么不只是"格式化"
 * 指标拿不到时如何显示，是一个**语义**问题而不是格式问题：
 * 旧实现把读不到的 TPS 显示为字面量 **"20.0"** —— 那正是"完美 TPS"。
 * 于是代理端（拿不到 TPS）的服主会看到一张报告"20.0 TPS 一切正常"的卡片，
 * 而真实情况是**这个指标根本没读到**。MSPT 那边用的是 "N/A"，两者语义还不一致。
 *
 * 现在的规则：**拿不到就明说拿不到**（统一的占位符），绝不回落成看起来正常的数值。
 */
object ServerMetricsText {

    /** 指标不可用时的统一占位符。 */
    const val MISSING = "N/A"

    /** TPS 的物理上限：超过它只可能是读数异常，显示上钳制（保留原行为）。 */
    private const val MAX_TPS = 20.0

    /**
     * 取某一时间窗的 TPS 文本。
     *
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
