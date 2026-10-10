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

/**
 * Payload 长度治理（基线 §5 的三层策略）。
 *
 * 取代旧实现的「对顶层 Map 的每个 String 一刀切截断到 500 字符且完全静默」：
 * 1. **展示文本**：按上限截断并**追加省略号 + 事后告警**，同时收敛组合符（防 Zalgo/ReDoS）；
 * 2. **二进制 / 图层**（`data:` URL）：**不做字符截断**，按单层字节上限与二进制总预算判定，
 *    超限**丢弃该层**（"丢坏层、保好层"），绝不产出坏图；
 * 3. **整体预算**：文本字符总量超过告警阈值时提示，便于排查。
 *
 * 递归穿透 Map / List / Array —— 旧实现只处理顶层，嵌套结构完全绕过，防护不对称。
 */
internal class PayloadGovernor(
    private val displayTextMax: Int,
    private val perLayerMaxBytes: Int,
    private val binaryTotalMaxBytes: Int,
    private val layersMax: Int,
    private val textTotalWarn: Int,
    private val warn: (String) -> Unit
) {
    private var binaryBytes = 0
    private var layerCount = 0
    private var droppedLayers = 0
    private var truncatedFields = 0
    private var totalChars = 0

    fun govern(payload: Any?): Any? = walk(payload, 0)

    /** 汇总输出告警，避免逐字段刷屏。 */
    fun report() {
        if (droppedLayers > 0) {
            warn(
                "[PlumBot-Render] 已丢弃 $droppedLayers 个超限图层" +
                    "（单层上限 ${perLayerMaxBytes}B / 二进制总量 ${binaryTotalMaxBytes}B / 图层数上限 $layersMax）"
            )
        }
        if (truncatedFields > 0) {
            warn("[PlumBot-Render] $truncatedFields 个文本字段超过 $displayTextMax 字符，已截断并标记省略号")
        }
        if (textTotalWarn > 0 && totalChars > textTotalWarn) {
            warn("[PlumBot-Render] payload 文本总量 $totalChars 字符，超过告警阈值 $textTotalWarn")
        }
    }

    private fun walk(value: Any?, depth: Int): Any? {
        if (depth > MAX_DEPTH) return null
        return when (value) {
            null -> null
            is String -> governString(value)
            is Map<*, *> -> value.entries.associate { (key, item) -> key to walk(item, depth + 1) }
            is List<*> -> value.map { walk(it, depth + 1) }
            is Set<*> -> value.map { walk(it, depth + 1) }
            is Array<*> -> value.map { walk(it, depth + 1) }
            else -> value
        }
    }

    private fun governString(value: String): Any? {
        if (value.startsWith(DATA_URL_PREFIX, ignoreCase = true)) {
            return governBinary(value)
        }

        totalChars += value.length
        val cleaned = ZALGO.replace(value, "")
        return if (cleaned.length > displayTextMax) {
            truncatedFields++
            cleaned.take(displayTextMax) + TRUNCATION_MARK
        } else {
            cleaned
        }
    }

    private fun governBinary(value: String): Any? {
        layerCount++
        if (layerCount > layersMax) {
            droppedLayers++
            return null
        }
        // base64 近似：4 个字符 ≈ 3 字节
        val approxBytes = value.length / 4 * 3
        if (approxBytes > perLayerMaxBytes || binaryBytes + approxBytes > binaryTotalMaxBytes) {
            droppedLayers++
            return null
        }
        binaryBytes += approxBytes
        return value
    }

    private companion object {
        const val MAX_DEPTH = 16
        const val DATA_URL_PREFIX = "data:"
        const val TRUNCATION_MARK = "…"
        val ZALGO = Regex("\\p{M}{3,}")
    }
}
