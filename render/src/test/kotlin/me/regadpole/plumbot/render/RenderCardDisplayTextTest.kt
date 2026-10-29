/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 */

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [RenderCard.displayText] 契约测试。
 *
 * 纯文本降级与 AWT 出图共用该方法：标题里的服务器名/标题必须进入文字版，
 * 但标题已经作为正文首行时不能重复拼接。
 */
class RenderCardDisplayTextTest {

    @Test
    fun `无标题时原样返回正文`() {
        assertEquals("正文", RenderCard(text = "正文").displayText)
    }

    @Test
    fun `空白标题视为无标题`() {
        assertEquals("正文", RenderCard(text = "正文", title = "   ").displayText)
    }

    @Test
    fun `标题合并为首行`() {
        val card = RenderCard(text = "TPS: 20.0", title = "生存服")
        assertEquals("生存服\nTPS: 20.0", card.displayText)
    }

    @Test
    fun `首行与标题相同时不重复`() {
        val card = RenderCard(text = "玩家已绑定\nQQ: 123", title = "玩家已绑定")
        assertEquals("玩家已绑定\nQQ: 123", card.displayText)
    }

    @Test
    fun `首行带色码且以标题开头时不重复`() {
        val card = RenderCard(text = "§a玩家已绑定\nQQ: 123", title = "§a玩家已绑定")
        assertEquals("§a玩家已绑定\nQQ: 123", card.displayText)
    }

    @Test
    fun `首行以标题为前缀时不重复`() {
        val card = RenderCard(text = "生存服：TPS 20.0", title = "生存服")
        assertEquals("生存服：TPS 20.0", card.displayText)
    }

    @Test
    fun `data 不参与文本投影`() {
        val card = RenderCard(text = "正文", data = mapOf("players" to listOf("a", "b")))
        assertEquals("正文", card.displayText)
    }
}
