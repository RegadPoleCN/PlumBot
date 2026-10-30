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

/**
 * 极简 JSON 解析器：渲染子系统**不引入 gson**（理由同 [JsonEncoder]）。
 * 正版皮肤链路必须解析两段 JSON —— `sessionserver.mojang.com` 的响应，
 * 以及其 `properties[0].value` 里那段 base64 之后嵌套的 texture JSON。
 *
 * 只支持标准 JSON 子集（对象/数组/字符串/数字/布尔/null），不做注释与尾逗号等扩展；
 * 解析失败抛 [IllegalArgumentException]，调用方需自行 `runCatching`。
 */
internal object JsonParser {

    fun parse(text: String): Any? {
        val cursor = Cursor(text)
        cursor.skipWhitespace()
        val value = cursor.readValue()
        cursor.skipWhitespace()
        if (!cursor.isAtEnd) throw IllegalArgumentException("JSON 尾部存在多余内容（位置 ${cursor.index}）")
        return value
    }

    /**
     * 按路径取值：`String` 键取对象字段，`Int` 键取数组下标，路径不通返回 null。
     * 例：`path(root, "properties", 0, "value")`
     */
    fun path(root: Any?, vararg keys: Any): Any? {
        var current: Any? = root
        for (key in keys) {
            current = when {
                key is String && current is Map<*, *> -> current[key]
                key is Int && current is List<*> -> current.getOrNull(key)
                else -> return null
            }
        }
        return current
    }

    private class Cursor(private val text: String) {
        var index = 0
        val isAtEnd: Boolean get() = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (isAtEnd) throw IllegalArgumentException("JSON 意外结束")
            return when (val c = text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else
                    throw IllegalArgumentException("非法 JSON 字符 '$c'（位置 $index）")
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') { index++; return result }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                result[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> { index++; return result }
                    else -> throw IllegalArgumentException("对象缺少 ',' 或 '}'（位置 $index）")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') { index++; return result }
            while (true) {
                result.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> { index++; return result }
                    else -> throw IllegalArgumentException("数组缺少 ',' 或 ']'（位置 $index）")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (isAtEnd) throw IllegalArgumentException("字符串未闭合")
                when (val c = text[index++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (isAtEnd) throw IllegalArgumentException("转义未完成")
                        when (val esc = text[index++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = text.substring(index, (index + 4).coerceAtMost(text.length))
                                if (hex.length < 4) throw IllegalArgumentException("\\u 转义不完整")
                                sb.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> throw IllegalArgumentException("非法转义 '\\$esc'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Double {
            val start = index
            if (peek() == '-') index++
            while (!isAtEnd && (text[index].isDigit() || text[index] in ".eE+-")) index++
            return text.substring(start, index).toDoubleOrNull()
                ?: throw IllegalArgumentException("非法数字 '${text.substring(start, index)}'")
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, index)) {
                throw IllegalArgumentException("非法字面量（位置 $index）")
            }
            index += literal.length
            return value
        }

        private fun expect(c: Char) {
            if (isAtEnd || text[index] != c) {
                throw IllegalArgumentException("期望 '$c'（位置 $index）")
            }
            index++
        }

        private fun peek(): Char =
            if (isAtEnd) throw IllegalArgumentException("JSON 意外结束") else text[index]
    }
}
