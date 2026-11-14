// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.filter

import java.util.ArrayDeque

class AhoCorasickMatcher(words: Collection<String>) {

    private class Node {
        val children: MutableMap<Char, Node> = HashMap()
        var fail: Node? = null
        var matchedLength: Int = 0
    }

    private val root = Node()

    init {
        // 1. 构建 Trie 树：词与被扫描文本必须走同一归一化管线，两侧折叠结果才可比。
        //    matchedLength 记录折叠后长度，命中区间再经映射切回原文。
        for (word in words) {
            if (word.isBlank()) continue
            val folded = TextNormalizer.normalize(word).text
            if (folded.isEmpty()) continue
            var current = root
            for (ch in folded) {
                current = current.children.computeIfAbsent(ch) { Node() }
            }
            current.matchedLength = folded.length
        }

        // 2. 构建 Fail 失败指针 (BFS 广度优先遍历)
        val queue = ArrayDeque<Node>()
        for (child in root.children.values) {
            child.fail = root
            queue.add(child)
        }

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            for ((ch, child) in current.children) {
                var fallback = current.fail
                while (fallback != null && !fallback.children.containsKey(ch)) {
                    fallback = fallback.fail
                }
                child.fail = fallback?.children?.get(ch) ?: root
                queue.add(child)
            }
        }
    }

    /** 单次扫描文本，返回所有命中的敏感词区间（含区间内原文）。区间以原文索引为准。 */
    fun match(text: String): List<MatchSpan> {
        val spans = mutableListOf<MatchSpan>()
        val folded = TextNormalizer.normalize(text)
        val foldedText = folded.text
        var current = root

        for (i in foldedText.indices) {
            val ch = foldedText[i]
            while (current != root && !current.children.containsKey(ch)) {
                current = current.fail ?: root
            }
            current = current.children[ch] ?: root

            var temp: Node? = current
            while (temp != null && temp != root) {
                val length = temp.matchedLength
                if (length > 0) {
                    // 折叠串里的命中区间映射回原文：起点取首字符的原文起点，终点取末字符的原文终点，
                    // 并向两端吞并紧邻的被剥离字符（词内/词尾零宽与组合符号），替换后不残留。
                    val start = folded.inclusiveStart(i - length + 1)
                    val end = folded.inclusiveEnd(i)
                    spans.add(MatchSpan(start, end, text.substring(start, end)))
                }
                temp = temp.fail
            }
        }
        return spans
    }

    /** 按命中区间脱敏替换；区间重叠时先合并，避免替换错位。 */
    fun replace(text: String, replacement: String): String {
        val spans = match(text)
        if (spans.isEmpty()) return text

        val mergedSpans = mergeSpans(spans)
        val sb = StringBuilder()
        var lastIndex = 0

        for ((start, end) in mergedSpans) {
            sb.append(text, lastIndex, start)
            sb.append(replacement)
            lastIndex = end
        }
        if (lastIndex < text.length) {
            sb.append(text, lastIndex, text.length)
        }
        return sb.toString()
    }

    private fun mergeSpans(spans: List<MatchSpan>): List<MatchSpan> {
        if (spans.size <= 1) return spans
        val sorted = spans.sortedBy { it.start }
        val merged = mutableListOf<MatchSpan>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end) {
                val newEnd = maxOf(current.end, next.end)
                current = MatchSpan(current.start, newEnd, "")
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    data class MatchSpan(val start: Int, val end: Int, val word: String)
}
