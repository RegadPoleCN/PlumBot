// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.pipeline.PayloadGovernor
import me.regadpole.plumbot.render.support.RenderSecurity
import me.regadpole.plumbot.render.support.RenderSecurity.toBrowserSafeUrl

/**
 * 渲染期守卫与长度治理的契约测试。
 *
 * 三组约束：
 * - 主机白名单（含"看似同域"的绕过尝试；只约束起点、不做 IP 审查）；
 * - `file://` 沙箱收紧 + 路径百分号编码；
 * - 三层长度治理（data URL 不被文本截断、嵌套结构同样受约束）。
 */
class RenderSecurityAndGovernorTest {

    private val tempRoot: File =
        File(System.getProperty("java.io.tmpdir"), "plumbot-render-test").apply { mkdirs() }

    @Test
    fun `含空格与中文的路径被正确百分号编码`() {
        val file = File(tempRoot, "主题 目录/index.html")
        val url = file.toBrowserSafeUrl()

        assertTrue(url.startsWith("file:///"), "应为标准 file:/// 形式：$url")
        assertTrue(url.contains("%20"), "空格应被编码：$url")
        assertFalse(url.contains(" "), "URL 中不应残留原始空格：$url")
    }

    @Test
    fun `只放行主题根目录之内的文件`() {
        val inside = File(tempRoot, "default/index.html").toBrowserSafeUrl()
        val nested = File(tempRoot, "default/assets/app.js").toBrowserSafeUrl()
        val sibling = File(tempRoot.parentFile, "outside.html").toBrowserSafeUrl()

        assertTrue(RenderSecurity.isFileUrlWithinRoot(inside, tempRoot))
        assertTrue(RenderSecurity.isFileUrlWithinRoot(nested, tempRoot))
        assertFalse(
            RenderSecurity.isFileUrlWithinRoot(sibling, tempRoot),
            "根目录之外的文件必须被拒绝（旧实现放行整个数据目录）"
        )
    }

    @Test
    fun `非 file 协议不被文件守卫放行`() {
        assertFalse(RenderSecurity.isFileUrlWithinRoot("https://example.com/a", tempRoot))
    }

    @Test
    fun `白名单大小写不敏感且支持子域`() {
        val allowed = setOf("q1.qlogo.cn", "crafatar.com")

        assertTrue(RenderSecurity.isHostAllowed("q1.qlogo.cn", allowed))
        assertTrue(RenderSecurity.isHostAllowed("Q1.QLOGO.CN", allowed))
        assertTrue(RenderSecurity.isHostAllowed("www.crafatar.com", allowed), "子域应命中")
    }

    @Test
    fun `白名单不得被后缀相似域名绕过`() {
        val allowed = setOf("crafatar.com")

        assertFalse(RenderSecurity.isHostAllowed("evil-crafatar.com", allowed))
        assertFalse(RenderSecurity.isHostAllowed("crafatar.com.evil.com", allowed))
        assertFalse(RenderSecurity.isHostAllowed("notcrafatar.com", allowed))
        assertFalse(RenderSecurity.isHostAllowed("", allowed))
    }

    private fun governor(
        displayTextMax: Int = 10,
        perLayerMaxBytes: Int = 1000,
        binaryTotalMaxBytes: Int = 2000,
        layersMax: Int = 4,
        warns: MutableList<String> = mutableListOf()
    ) = PayloadGovernor(
        displayTextMax = displayTextMax,
        perLayerMaxBytes = perLayerMaxBytes,
        binaryTotalMaxBytes = binaryTotalMaxBytes,
        layersMax = layersMax,
        textTotalWarn = 0,
        warn = { warns += it }
    )

    private fun dataUrl(payloadChars: Int) = "data:image/png;base64," + "A".repeat(payloadChars)

    @Test
    fun `data URL 不被文本截断`() {
        val warns = mutableListOf<String>()
        val url = dataUrl(400) // ≈ 316 字节，远小于单层上限
        val result = governor(warns = warns).govern(mapOf("avatarUrl" to url)) as Map<*, *>

        assertEquals(url, result["avatarUrl"], "data URL 必须原样保留，否则会变成坏图")
        assertTrue(warns.isEmpty(), "未超限时不应产生告警")
    }

    @Test
    fun `超限图层被丢弃而不是被截断`() {
        val warns = mutableListOf<String>()
        val g = governor(perLayerMaxBytes = 1000, layersMax = 8, warns = warns)
        val result = g.govern(
            mapOf(
                "good" to dataUrl(40),
                "bad" to dataUrl(8000)
            )
        ) as Map<*, *>
        g.report()

        assertNull(result["bad"], "超限图层应被丢弃")
        assertNotNull(result["good"], "正常图层必须保留（丢坏层、保好层）")
        assertTrue(warns.any { it.contains("丢弃") }, "应输出聚合告警：$warns")
    }

    @Test
    fun `图层数超限时丢弃多余图层`() {
        val warns = mutableListOf<String>()
        val g = governor(layersMax = 2, warns = warns)
        val result = g.govern(
            mapOf(
                "l1" to dataUrl(8),
                "l2" to dataUrl(8),
                "l3" to dataUrl(8)
            )
        ) as Map<*, *>
        g.report()

        assertNotNull(result["l1"])
        assertNotNull(result["l2"])
        assertNull(result["l3"], "超过图层数上限的层应被丢弃")
    }

    @Test
    fun `展示文本截断并标记但绝不静默`() {
        val warns = mutableListOf<String>()
        val g = governor(displayTextMax = 10, warns = warns)
        val result = g.govern(mapOf("text" to "x".repeat(50))) as Map<*, *>
        g.report()

        val text = result["text"] as String
        assertEquals(11, text.length, "应为 10 字符 + 省略号")
        assertTrue(text.endsWith("…"))
        assertTrue(warns.any { it.contains("截断") }, "截断必须有告警（旧实现完全静默）：$warns")
    }

    @Test
    fun `嵌套结构与列表同样受治理`() {
        val warns = mutableListOf<String>()
        val g = governor(displayTextMax = 10, perLayerMaxBytes = 500, warns = warns)
        val result = g.govern(
            mapOf(
                "outer" to mapOf("inner" to "y".repeat(50)),
                "list" to listOf("z".repeat(50), dataUrl(8000))
            )
        ) as Map<*, *>
        g.report()

        val inner = (result["outer"] as Map<*, *>)["inner"] as String
        assertEquals(11, inner.length, "嵌套 Map 的字符串同样应受约束")

        val list = result["list"] as List<*>
        assertEquals(11, (list[0] as String).length, "列表内字符串同样应受约束")
        assertNull(list[1], "列表内的超限图层同样应被丢弃")
    }

    @Test
    fun `组合符收敛只作用于展示文本`() {
        val zalgo = "a" + "\u0301".repeat(6) + "b"
        val warns = mutableListOf<String>()
        val g = governor(displayTextMax = 100, warns = warns)
        val result = g.govern(mapOf("name" to zalgo, "avatarUrl" to dataUrl(40))) as Map<*, *>

        val name = result["name"] as String
        assertFalse(name.contains("\u0301\u0301\u0301"), "连续组合符应被收敛：$name")
        assertEquals(dataUrl(40), result["avatarUrl"], "data URL 不应被组合符规则触碰")
    }

    @Test
    fun `RenderCard 正文不截断但 data 仍受治理`() {
        val warns = mutableListOf<String>()
        val g = governor(displayTextMax = 10, warns = warns)
        val card = RenderCard(
            text = "x".repeat(50),
            title = "t".repeat(50),
            data = mapOf("long" to "y".repeat(50), "num" to 1)
        )
        val result = g.govern(card) as RenderCard
        g.report()

        assertEquals(50, result.text.length, "card.text 是正文真源，不能截断成与 fallback 不同的内容")
        assertEquals(11, result.title!!.length, "title 仍应受展示文本上限约束")
        assertEquals(11, (result.data["long"] as String).length, "data 内字段仍应受治理")
        assertEquals(1, result.data["num"])
        assertTrue(warns.any { it.contains("截断") }, "data 截断必须有告警：$warns")
    }

    @Test
    fun `标量与空值原样透传`() {
        val g = governor()
        val result = g.govern(mapOf("num" to 42, "flag" to true, "nothing" to null)) as Map<*, *>

        assertEquals(42, result["num"])
        assertEquals(true, result["flag"])
        assertNull(result["nothing"])
    }
}
