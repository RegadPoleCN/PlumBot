// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline

import me.regadpole.plumbot.render.RenderCard

/**
 * Payload 长度治理，三层策略：
 *
 * 1. **展示文本**：按上限截断并**追加省略号 + 事后告警**，同时收敛组合符（防 Zalgo/ReDoS）；
 * 2. **二进制 / 图层**（`data:` URL）：**不做字符截断**，按单层字节上限与二进制总预算判定，
 *    超限**丢弃该层**（丢坏层、保好层），绝不产出坏图；
 * 3. **整体预算**：文本字符总量超过告警阈值时提示，便于排查。
 *
 * 递归穿透 Map / List / Array：只治理顶层时嵌套结构会完全绕过，防护不对称。
 */
internal class PayloadGovernor(
    private val displayTextMax: Int,
    private val perLayerMaxBytes: Int,
    private val binaryTotalMaxBytes: Int,
    private val layersMax: Int,
    private val textTotalWarn: Int,
    private val cardTextMax: Int = 0,
    private val warn: (String) -> Unit
) {
    private var binaryBytes = 0
    private var layerCount = 0
    private var droppedLayers = 0
    private var truncatedFields = 0
    private var totalChars = 0

    /** 卡片 `text` 超过 [cardTextMax]：调用方应放弃出图、直接发纯文本。 */
    var cardTextOverflow: Boolean = false
        private set

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
        if (cardTextOverflow) {
            warn(
                "[PlumBot-Render] 卡片正文超过 card_text_max=$cardTextMax 字符，已放弃出图并改为发送纯文本" +
                    "（截断正文会让图片与文字变成两套内容，因此这里 fail-closed）"
            )
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
            is RenderCard -> governCard(value, depth)
            is Map<*, *> -> value.entries.associate { (key, item) -> key to walk(item, depth + 1) }
            is List<*> -> value.map { walk(it, depth + 1) }
            is Set<*> -> value.map { walk(it, depth + 1) }
            is Array<*> -> value.map { walk(it, depth + 1) }
            else -> value
        }
    }

    /**
     * 统一卡片：`text` 是正文真源，**不截断**（截断会让图片与纯文本降级产生两套内容）；
     * 只计入总量告警。`title` 与 `data` 仍按普通字段治理，避免头像 data URL 等绕过预算。
     */
    private fun governCard(card: RenderCard, depth: Int): RenderCard {
        totalChars += card.text.length
        // 正文不截断（截断会与纯文本降级分叉），但超大正文仍要 fail-closed：否则一条异常
        // 消息就能把整段文本塞进无头浏览器，渲染超时后同样只剩降级。
        if (cardTextMax > 0 && card.text.length > cardTextMax) cardTextOverflow = true
        val governedData = (walk(card.data, depth + 1) as? Map<*, *>)
            ?.entries
            ?.associate { it.key.toString() to it.value }
            ?: emptyMap()
        return RenderCard(
            text = card.text,
            title = card.title?.let { governString(it) as? String },
            data = governedData
        )
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
