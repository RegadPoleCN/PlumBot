// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.filter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 过滤器匹配的归一化与索引映射回归：
 * 旧实现 lowercase() 变长折叠 + 直接用折叠索引切原文，会越界崩溃或替换错位；
 * 零宽/全角/组合变音字符可打断 Trie 匹配绕过过滤。
 */
class AhoCorasickMatcherTest {

    @Test
    fun `plain case-insensitive match still works`() {
        val matcher = AhoCorasickMatcher(listOf("badword"))
        val spans = matcher.match("say BADWORD now")
        assertEquals(1, spans.size)
        assertEquals(4, spans[0].start)
        assertEquals(11, spans[0].end)
        assertEquals("BADWORD", spans[0].word)
        assertEquals("say *** now", matcher.replace("say BADWORD now", "***"))
    }

    @Test
    fun `zero width characters cannot split a blocked word`() {
        val matcher = AhoCorasickMatcher(listOf("敏感词"))
        val text = "敏\u200B感\u200B词"
        val spans = matcher.match(text)
        assertEquals(1, spans.size, "零宽字符不得打断匹配")
        assertEquals(0, spans[0].start)
        assertEquals(text.length, spans[0].end, "命中区间必须覆盖夹在词里的零宽字符")
        assertEquals("***", matcher.replace(text, "***"), "整段替换后不得残留任何原文")
    }

    @Test
    fun `full width forms fold onto the ascii word`() {
        val matcher = AhoCorasickMatcher(listOf("sex"))
        val spans = matcher.match("ＳＥＸ")
        assertEquals(1, spans.size, "全角形式必须折回半角命中")
        assertEquals(0, spans[0].start)
        assertEquals(3, spans[0].end, "全角串的命中区间必须映射回原文坐标")
        assertEquals("***", matcher.replace("ＳＥＸ", "***"))
    }

    @Test
    fun `combining marks cannot bypass the filter`() {
        // 文本用分解形式（e + U+0301），词库用预合成形式（é）——两侧归一化后必须一致
        val matcher = AhoCorasickMatcher(listOf("café"))
        val spans = matcher.match("cafe\u0301")
        assertEquals(1, spans.size, "组合变音字符不得绕过过滤")
        assertEquals(0, spans[0].start)
        assertEquals(5, spans[0].end, "命中区间必须吞并词尾的组合符号，替换后不得残留")
        assertEquals("***", matcher.replace("cafe\u0301", "***"))
    }

    @Test
    fun `length changing lower case cannot crash or misalign spans`() {
        // İ 的 lowercase() 变长（旧实现按折叠串索引切原文会越界），连字 ﬁ 同类
        val matcher = AhoCorasickMatcher(listOf("ik"))
        val spans = matcher.match("İk")
        assertEquals(1, spans.size)
        assertEquals(0, spans[0].start)
        assertEquals(2, spans[0].end)
        assertEquals("***", matcher.replace("İk", "***"))

        val ligature = AhoCorasickMatcher(listOf("fix"))
        val spans2 = ligature.match("ﬁx")
        assertEquals(1, spans2.size, "连字经 NFKD 分解后必须仍命中")
        assertEquals(0, spans2[0].start)
        assertEquals(2, spans2[0].end, "ﬁ 是单个 UTF-16 字符，原串共 2 个字符")
        assertEquals("***", ligature.replace("ﬁx", "***"))
    }

    @Test
    fun `overlapping hits are merged without corrupting text`() {
        val matcher = AhoCorasickMatcher(listOf("abc", "bcd"))
        // "abc"=[1,4) 与 "bcd"=[2,5) 合并为 [1,5)="abcd"，替换后剩首尾字符
        assertEquals("x#y", matcher.replace("xabcdy", "#"))
    }

    @Test
    fun `clean text passes through untouched`() {
        val matcher = AhoCorasickMatcher(listOf("badword"))
        val text = "hello 你好 world"
        assertTrue(matcher.match(text).isEmpty())
        assertEquals(text, matcher.replace(text, "***"))
    }
}
