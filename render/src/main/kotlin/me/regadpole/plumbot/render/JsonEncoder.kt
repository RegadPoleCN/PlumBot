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
 * 极简 JSON 编码器（仅覆盖渲染载荷用到的 JSON 子集：对象/数组/字符串/数字/布尔/null）。
 *
 * **为什么不用 Gson**：`:render` 原先 `compileOnly(libs.gson)`，但 JVM 在**校验**驱动类时即需要
 * `com.google.gson.Gson`，于是 `engine=awt` 的用户也会因缺少 gson 而 `NoClassDefFoundError`
 * （Libby 只在 Bukkit 端加载 gson，Velocity/Hytale 未加载）。自持一个编码器后，
 * `:render` 不再依赖任何 JSON 库，该失败模式被彻底消除。
 */
internal object JsonEncoder {

    fun encode(value: Any?): String {
        val sb = StringBuilder(64)
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value)
            is Float -> if (value.isFinite()) sb.append(value) else sb.append("null")
            is Double -> if (value.isFinite()) sb.append(value) else sb.append("null")
            is Map<*, *> -> writeObject(sb, value)
            is Iterable<*> -> writeArray(sb, value)
            is Array<*> -> writeArray(sb, value.asIterable())
            is CharArray -> writeString(sb, String(value))
            is Char -> writeString(sb, value.toString())
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeObject(sb: StringBuilder, map: Map<*, *>) {
        sb.append('{')
        var first = true
        for ((key, item) in map) {
            if (!first) sb.append(',')
            first = false
            writeString(sb, key?.toString() ?: "null")
            sb.append(':')
            write(sb, item)
        }
        sb.append('}')
    }

    private fun writeArray(sb: StringBuilder, values: Iterable<*>) {
        sb.append('[')
        var first = true
        for (item in values) {
            if (!first) sb.append(',')
            first = false
            write(sb, item)
        }
        sb.append(']')
    }

    private fun writeString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') {
                    sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }
}
