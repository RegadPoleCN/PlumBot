// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

/**
 * 极简 JSON 编码器，只覆盖渲染载荷用到的 JSON 子集（对象/数组/字符串/数字/布尔/null）。
 *
 * **不用 Gson 的原因**：JVM 在**校验**驱动类时即需要 `com.google.gson.Gson`，而 Libby 只在
 * Bukkit 端加载 gson，于是 `engine=awt` 的用户在 Velocity/Hytale 也会 `NoClassDefFoundError`。
 * 自持编码器后 `:render` 不依赖任何 JSON 库，该失败模式被消除。
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
            is RenderCard -> writeObject(
                sb,
                linkedMapOf(
                    "text" to value.text,
                    "title" to value.title,
                    "data" to value.data
                )
            )
            is Map<*, *> -> writeObject(sb, value)
            is Iterable<*> -> writeArray(sb, value)
            is Array<*> -> writeArray(sb, value.asIterable())
            is TextPayload -> writeObject(
                sb,
                linkedMapOf(
                    "body" to value.body,
                    "title" to value.title,
                    "ampersandCodes" to value.ampersandCodes
                )
            )
            is CharArray -> writeString(sb, String(value))
            is Char -> writeString(sb, value.toString())
            // 二进制与枚举若掉进 toString()，ByteArray 会变成 "[B@1a2b3c"，模板拿到无意义
            // 字符串。这里给出确定形状，避免「看起来有值其实不可用」。
            is ByteArray -> writeString(sb, java.util.Base64.getEncoder().encodeToString(value))
            is Enum<*> -> writeString(sb, value.name)
            is IntArray -> writeArray(sb, value.asIterable())
            is LongArray -> writeArray(sb, value.asIterable())
            is ShortArray -> writeArray(sb, value.asIterable())
            is DoubleArray -> writeArray(sb, value.asIterable())
            is FloatArray -> writeArray(sb, value.asIterable())
            is BooleanArray -> writeArray(sb, value.asIterable())
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
