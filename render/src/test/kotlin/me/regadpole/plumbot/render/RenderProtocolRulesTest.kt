// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 运行时协议字段自检的行为约束。
 *
 * 这些断言替代了从前构建期的「读前端 types.ts 逐字段比对」：那套规则随前端迁出
 * 独立仓库而删除，字段漂移必须由运行时接住，所以这里固定住四个口径 ——
 * 声明为空表示无约束、必填字段缺失与多余字段都要报、类型带 `?` 的可选字段缺失不算漂移、
 * 非 RenderCard 载荷不参与比对。
 */
class RenderProtocolRulesTest {

    @Test
    fun `清单资源可读且覆盖全部 route`() {
        assertEquals(17, RenderProtocolRules.routeCount, RenderProtocolRules.describe())
        assertTrue(RenderProtocolRules.describe().startsWith("protocol fields: 17 routes"), RenderProtocolRules.describe())
    }

    @Test
    fun `载荷缺少协议声明的字段会被报出`() {
        val card = RenderCard(
            text = "在线 0 人",
            data = mapOf("serverName" to "Aurora", "tps" to 20.0)
        )
        val diff = RenderProtocolRules.diff("status", card)
        assertNotNull(diff)
        assertTrue(diff.contains("status"), diff)
        assertTrue(diff.contains("缺失"), diff)
        assertTrue(diff.contains("health"), diff)
    }

    @Test
    fun `载荷多出协议未声明的字段会被报出`() {
        val card = RenderCard(
            text = "在线 0 人",
            data = mapOf(
                "serverName" to "Aurora", "tps" to 20.0, "mspt" to 1.0,
                "usedMem" to 1L, "maxMem" to 2L, "online" to 0, "health" to "正常",
                "ghostField" to 1
            )
        )
        val diff = RenderProtocolRules.diff("status", card)
        assertNotNull(diff)
        assertTrue(diff.contains("多出"), diff)
        assertTrue(diff.contains("ghostField"), diff)
        assertTrue(!diff.contains("缺失"), diff)
    }

    @Test
    fun `字段完全一致时不报告`() {
        val card = RenderCard(
            text = "在线 0 人",
            data = mapOf(
                "serverName" to "Aurora", "tps" to 20.0, "mspt" to 1.0,
                "usedMem" to 1L, "maxMem" to 2L, "online" to 0, "health" to "正常"
            )
        )
        assertNull(RenderProtocolRules.diff("status", card))
    }

    @Test
    fun `声明为空对象的 route 不做约束`() {
        val card = RenderCard(text = "已加载", data = mapOf("anything" to 1))
        assertNull(RenderProtocolRules.diff("load", card))
    }

    @Test
    fun `未知 route 与非 RenderCard 载荷不参与比对`() {
        val card = RenderCard(text = "x", data = mapOf("nope" to 1))
        assertNull(RenderProtocolRules.diff("noSuchRoute", card))
        assertNull(RenderProtocolRules.diff("status", TextPayload(body = "纯文本")))
        assertNull(RenderProtocolRules.diff("status", null))
    }

    @Test
    fun `可选字段缺失不算漂移`() {
        // console 的 channel/code/durationMs 在清单里声明为可选（"string?" / "number?"）
        val card = RenderCard(text = "执行完毕", data = mapOf("state" to "done"))
        assertNull(RenderProtocolRules.diff("console", card))

        // 但出现协议里根本没有的键仍要报
        val drifted = RenderCard(text = "执行完毕", data = mapOf("state" to "done", "ghost" to 1))
        val diff = RenderProtocolRules.diff("console", drifted)
        assertNotNull(diff)
        assertTrue(diff.contains("ghost"), diff)
        assertTrue(!diff.contains("缺失"), diff)
    }

    @Test
    fun `同一差异只上报一次并计数`() {
        RenderProtocolRules.resetForTest()
        val card = RenderCard(text = "x", data = emptyMap())
        val diff = RenderProtocolRules.diff("status", card)
        assertNotNull(diff)
        val logged = mutableListOf<String>()
        assertTrue(RenderProtocolRules.report("status", diff) { logged += it })
        assertTrue(!RenderProtocolRules.report("status", diff) { logged += it })
        assertEquals(1, logged.size, logged.joinToString())
        assertEquals(1, RenderProtocolRules.mismatchCount())
        assertTrue(logged.single().contains("[PlumBot-Render]"), logged.single())
    }
}
