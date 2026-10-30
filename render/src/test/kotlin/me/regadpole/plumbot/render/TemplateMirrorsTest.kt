// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 模板候选序列：官方直链优先，再自动补 3 个公共加速站；`template_urls` 非空即完全覆盖。
 *
 * 这些断言钉住的是**顺序**与**覆盖关系**：顺序错了会让每次都先打不通的站点；
 * 覆盖关系错了会让用户显式配的地址被内置序列悄悄抢走。
 */
class TemplateMirrorsTest {

    private val official = TemplateMirrors.OFFICIAL_URL

    @Test
    fun `默认序列：官方直链打头，再跟三个加速站`() {
        val candidates = TemplateMirrors.candidates(null, emptyList())

        assertEquals(
            listOf(
                official,
                "https://ghproxy.net/" + official,
                "https://gh-proxy.com/" + official,
                "https://ghfast.top/" + official
            ),
            candidates
        )
    }

    @Test
    fun `template_urls 非空即完全覆盖内置序列`() {
        val custom = "https://mirror.example.org/plumbot/index.html"

        assertEquals(
            listOf(custom),
            TemplateMirrors.candidates(official, listOf(custom)),
            "显式配置必须完全覆盖，哪怕只写一个地址、哪怕它看起来像官方地址"
        )
    }

    @Test
    fun `template_urls 里的空白与重复会被清掉`() {
        assertEquals(
            listOf("https://a.example/x.html", "https://b.example/x.html"),
            TemplateMirrors.candidates(
                null,
                listOf("  https://a.example/x.html  ", "", "   ", null, "https://a.example/x.html", "https://b.example/x.html")
            )
        )
    }

    @Test
    fun `非 github 的自定义地址不加加速站前缀`() {
        val custom = "https://cdn.example.org/plumbot/index.html"

        assertEquals(
            listOf(custom),
            TemplateMirrors.candidates(custom, emptyList()),
            "前缀站只代理 GitHub；给自建地址加前缀只会得到 404"
        )
    }

    @Test
    fun `http 起点不会派生加速站候选`() {
        val plain = "http://github.com/RegadPoleCN/PlumBot-Web/releases/download/v1/index.html"

        assertEquals(listOf(plain), TemplateMirrors.candidates(plain, emptyList()))
        assertNotNull(TemplateMirrors.rejection(plain), "http 候选必须被准入判定拒绝（仅允许 https）")
    }

    @Test
    fun `准入判定只放行 https 直链`() {
        assertNull(TemplateMirrors.rejection("https://ghproxy.net/https://github.com/x/y.html"))
        assertNotNull(TemplateMirrors.rejection("ftp://example.org/x.html"))
        assertNotNull(TemplateMirrors.rejection("https:///x.html"), "缺少主机名要拒绝")
    }
}
