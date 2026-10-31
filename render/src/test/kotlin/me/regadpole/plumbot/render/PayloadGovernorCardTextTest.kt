// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.pipeline.ImageRenderManager
import me.regadpole.plumbot.render.pipeline.PayloadGovernor

/**
 * `render.limits.card_text_max` 的 fail-closed 契约。
 *
 * 为什么不是截断：`RenderCard.text` 同时喂给图片与文字两条投影，截断会让同一份载荷
 * 出现两种内容；因此超限时只置标志，由 `ImageRenderManager` 放弃出图并改发纯文本。
 */
class PayloadGovernorCardTextTest {

    private val warnings = mutableListOf<String>()

    private fun governor(cardTextMax: Int) = PayloadGovernor(
        displayTextMax = 1024,
        perLayerMaxBytes = 262_144,
        binaryTotalMaxBytes = 1_048_576,
        layersMax = 16,
        textTotalWarn = 262_144,
        cardTextMax = cardTextMax,
        warn = { warnings += it }
    )

    private fun card(text: String) = RenderCard(text = text, title = "标题")

    @Test
    fun overflowIsFlaggedInsteadOfTruncated() {
        val governor = governor(50)
        val long = "x".repeat(60)
        val governed = governor.govern(card(long)) as RenderCard
        assertTrue(governor.cardTextOverflow)
        assertEquals(60, governed.text.length, "正文必须保持完整，不能在治理阶段截断")
        governor.report()
        assertTrue(warnings.any { it.contains("card_text_max") }, warnings.toString())
    }

    @Test
    fun underLimitIsNotFlagged() {
        val governor = governor(50)
        governor.govern(card("短正文"))
        assertFalse(governor.cardTextOverflow)
        governor.report()
        assertTrue(warnings.none { it.contains("card_text_max") }, warnings.toString())
    }

    @Test
    fun zeroDisablesTheLimit() {
        val governor = governor(0)
        governor.govern(card("x".repeat(10_000)))
        assertFalse(governor.cardTextOverflow)
    }

    @Test
    fun plainMapPayloadIsUnaffected() {
        val governor = governor(5)
        governor.govern(linkedMapOf("text" to "x".repeat(100)))
        assertFalse(governor.cardTextOverflow, "非卡片载荷仍走原有的分层治理")
    }
}
