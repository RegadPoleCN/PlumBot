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
        // 1. 构建 Trie 树
        for (word in words) {
            if (word.isBlank()) continue
            var current = root
            for (ch in word.lowercase()) {
                current = current.children.computeIfAbsent(ch) { Node() }
            }
            current.matchedLength = word.length
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

    /**
     * 单次扫描文本，获取所有命中的敏感词区间及匹配词汇
     */
    fun match(text: String): List<MatchSpan> {
        val spans = mutableListOf<MatchSpan>()
        val lowerText = text.lowercase()
        var current = root

        for (i in lowerText.indices) {
            val ch = lowerText[i]
            while (current != root && !current.children.containsKey(ch)) {
                current = current.fail ?: root
            }
            current = current.children[ch] ?: root

            var temp: Node? = current
            while (temp != null && temp != root) {
                if (temp.matchedLength > 0) {
                    val start = i - temp.matchedLength + 1
                    val end = i + 1
                    spans.add(MatchSpan(start, end, text.substring(start, end)))
                }
                temp = temp.fail
            }
        }
        return spans
    }

    /**
     * 对命中的敏感词执行原地脱敏替换（处理区间重叠并替换为 replacement 字符串）
     */
    fun replace(text: String, replacement: String): String {
        val spans = match(text)
        if (spans.isEmpty()) return text

        // 区间合并，避免多个词重叠时替换错位
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
