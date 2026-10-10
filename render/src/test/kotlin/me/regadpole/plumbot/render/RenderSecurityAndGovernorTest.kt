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

import me.regadpole.plumbot.render.RenderSecurity.toBrowserSafeUrl
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 渲染期守卫与长度治理的契约测试。
 *
 * 覆盖批次 2 的三项 P1：
 * - **P1-9** SSRF 白名单（含"看似同域"的绕过尝试）；
 * - **P1-10** `file://` 沙箱收紧 + 路径百分号编码；
 * - **P1-13** 三层长度治理（data URL 不被文本截断、嵌套结构同样受约束）。
 */
class RenderSecurityAndGovernorTest {

    private val tempRoot: File =
        File(System.getProperty("java.io.tmpdir"), "plumbot-render-test").apply { mkdirs() }

    // ---------- P1-10：file:// 沙箱与路径编码 ----------

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

    // ---------- P1-9：主机白名单 ----------

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

    @Test
    fun `环回与私网地址被识别为受限目标`() {
        assertTrue(RenderSecurity.resolvesToBlockedAddress("127.0.0.1"))
        assertTrue(RenderSecurity.resolvesToBlockedAddress("10.0.0.1"))
        assertTrue(RenderSecurity.resolvesToBlockedAddress("172.17.0.1"), "172.16/12 必须覆盖 172.17")
        assertTrue(RenderSecurity.resolvesToBlockedAddress("172.31.255.254"))
        assertTrue(RenderSecurity.resolvesToBlockedAddress("192.168.1.1"))
        assertTrue(RenderSecurity.resolvesToBlockedAddress("169.254.169.254"), "云元数据地址必须拒绝")
        assertTrue(RenderSecurity.resolvesToBlockedAddress("100.64.0.1"), "CGNAT 段必须拒绝")
    }

    @Test
    fun `公网地址不被误判`() {
        assertFalse(RenderSecurity.resolvesToBlockedAddress("1.1.1.1"))
        assertFalse(RenderSecurity.resolvesToBlockedAddress("8.8.8.8"))
    }

    // ---------- P1-13：三层长度治理 ----------

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
    fun `标量与空值原样透传`() {
        val g = governor()
        val result = g.govern(mapOf("num" to 42, "flag" to true, "nothing" to null)) as Map<*, *>

        assertEquals(42, result["num"])
        assertEquals(true, result["flag"])
        assertNull(result["nothing"])
    }
}
