// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.filter

import java.text.Normalizer

/**
 * 归一化结果：[text] 是扫描用的折叠串；两个数组把折叠串里的每个字符映射回原文的
 * UTF-16 区间。匹配命中后必须经此映射切回原文 —— 零宽剔除、NFKD 分解等变长归一化
 * 会让"折叠串索引"与"原文索引"错位，直接用折叠索引切原文会越界或错切。
 */
internal class NormalizedText(
    val text: String,
    private val originStarts: IntArray,
    private val originEnds: IntArray,
    private val extendedStarts: IntArray,
    private val extendedEnds: IntArray,
) {
    /** 折叠串第 i 个字符来自原文的 [originStart(i), originEnd(i)) 区间。 */
    fun originStart(i: Int): Int = originStarts[i]
    fun originEnd(i: Int): Int = originEnds[i]

    /** 同 originStart，但向前吞并紧邻的被剥离字符（零宽/组合符号），替换后不残留不可见字符。 */
    fun inclusiveStart(i: Int): Int = extendedStarts[originStarts[i]]

    /** 同 originEnd，但向后吞并紧邻的被剥离字符（如词尾的组合变音符号），替换后不残留。 */
    fun inclusiveEnd(i: Int): Int = extendedEnds[originEnds[i]]
}

/**
 * 过滤器统一的归一化管线：NFKD 兼容分解 → 剥离组合符号/格式控制字符（零宽、
 * 双向控制等）→ 逐字符小写折叠。词库入树、文本扫描、忽略名单比较必须共用同一管线，
 * 两侧折叠结果才可比；任何一侧换实现都会造成绕过或误杀。
 */
internal object TextNormalizer {

    // 逐字符剥离的不可见字符类别：组合符号（Mn/Mc/Me）与格式控制（Cf，含零宽连接符、双向控制符）。
    private val STRIPPED_CATEGORIES = intArrayOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.FORMAT.toInt(),
    )

    // 通用类别剥离覆盖不到的零宽字符（部分零宽空格的类别不是 Cf，显式列出兜底）。
    private val STRIPPED_CHARS = setOf(
        0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF, 0x00AD
    ).map { it.toChar() }.toSet()

    fun normalize(input: String): NormalizedText {
        // 快路径：纯可打印 ASCII 无需 NFKD 与类别剥离，恒等映射 + 小写折叠即可。
        if (input.all { it.code in 0x20..0x7E }) {
            val sb = StringBuilder(input.length)
            for (ch in input) sb.append(ch.lowercaseChar())
            return NormalizedText(
                sb.toString(),
                IntArray(input.length) { it },
                IntArray(input.length) { it + 1 },
                IntArray(input.length + 1) { it },
                IntArray(input.length + 1) { it },
            )
        }

        val sb = StringBuilder(input.length)
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        val stripped = mutableListOf<Int>() // 成对记录被剥离字符的 [start, end)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val end = i + Character.charCount(cp)
            // NFKD 兼容分解：全角→半角、连字→拆分、预合成变音→基字符 + 组合符号。
            val decomposed = Normalizer.normalize(String(Character.toChars(cp)), Normalizer.Form.NFKD)
            var produced = false
            for (ch in decomposed) {
                if (ch in STRIPPED_CHARS || STRIPPED_CATEGORIES.any { Character.getType(ch) == it }) continue
                starts.add(i)
                ends.add(end)
                sb.append(ch.lowercaseChar())
                produced = true
            }
            if (!produced) {
                stripped.add(i)
                stripped.add(end)
            }
            i = end
        }
        // 相邻剥离区间的链式吞并：命中区间经 inclusiveStart/inclusiveEnd 扩展后，
        // 词首词尾的零宽/组合字符一并被替换覆盖，不会残留在结果里。
        val extStart = IntArray(input.length + 1) { it }
        for (j in 0 until stripped.size / 2) {
            val s = stripped[2 * j]; val e = stripped[2 * j + 1]
            extStart[e] = extStart[s]
        }
        val extEnd = IntArray(input.length + 1) { it }
        for (j in stripped.size / 2 - 1 downTo 0) {
            val s = stripped[2 * j]; val e = stripped[2 * j + 1]
            extEnd[s] = extEnd[e]
        }
        return NormalizedText(sb.toString(), starts.toIntArray(), ends.toIntArray(), extStart, extEnd)
    }

    /** 只需要折叠文本、不需要原文映射的场合（词库比较）。 */
    fun fold(input: String): String = normalize(input).text
}
