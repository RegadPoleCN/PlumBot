// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.pipeline.PayloadGovernor

/**
 * TextPayload 与 Map 键也要进入文本治理：整体透传会让"正文/标题/键"绕过截断与
 * Zalgo 收敛，超长键还会把无头浏览器的 DOM 与 JSON 预算一并撑爆。
 */
class PayloadGovernorTextPayloadTest {

    private val warnings = mutableListOf<String>()

    private fun governor() = PayloadGovernor(
        displayTextMax = 20,
        perLayerMaxBytes = 262_144,
        binaryTotalMaxBytes = 1_048_576,
        layersMax = 16,
        textTotalWarn = 262_144,
        warn = { warnings += it }
    )

    @Test
    fun textPayloadBodyAndTitleAreGoverned() {
        val out = governor().govern(TextPayload(body = "x".repeat(100), title = "t".repeat(100))) as TextPayload
        assertEquals(21, out.body.length, "正文须截断到上限并追加省略号")
        assertTrue(out.body.endsWith("…"), out.body)
        assertEquals(21, out.title!!.length, "标题须按同一上限治理")
    }

    @Test
    fun textPayloadAmpersandFlagIsPreserved() {
        val out = governor().govern(TextPayload(body = "hi", ampersandCodes = true)) as TextPayload
        assertTrue(out.ampersandCodes, "治理不得丢失 & 色码标记")
        assertEquals("hi", out.body)
    }

    @Test
    fun mapKeysAreGovernedToo() {
        val out = governor().govern(linkedMapOf("k".repeat(100) to "v")) as Map<*, *>
        assertEquals(21, out.keys.single().toString().length, "Map 键须与值同规则截断")
    }

    @Test
    fun nonStringKeysPassThrough() {
        val out = governor().govern(linkedMapOf(1 to "a", true to "b", null to "c")) as Map<*, *>
        assertEquals(setOf(1, true, null), out.keys, "非字符串键原样保留")
    }

    @Test
    fun zalgoRunsInKeysAreCollapsed() {
        val key = "a" + "\u0301".repeat(10)
        val out = governor().govern(linkedMapOf(key to "v")) as Map<*, *>
        assertEquals("a", out.keys.single(), "键里的组合符长串须被收敛")
    }
}