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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [JsonEncoder] 契约测试。
 *
 * 该编码器取代 Gson，用于把渲染载荷注入无头页面 —— 自持实现消除了
 * 「`:render` 在 `engine=awt` 时仍要求运行期存在 gson」的失败模式。
 */
class JsonEncoderTest {

    @Test
    fun `标量编码`() {
        assertEquals("null", JsonEncoder.encode(null))
        assertEquals("\"abc\"", JsonEncoder.encode("abc"))
        assertEquals("true", JsonEncoder.encode(true))
        assertEquals("false", JsonEncoder.encode(false))
        assertEquals("42", JsonEncoder.encode(42))
        assertEquals("42", JsonEncoder.encode(42L))
        assertEquals("1.5", JsonEncoder.encode(1.5))
    }

    @Test
    fun `非有限浮点数降级为 null`() {
        assertEquals("null", JsonEncoder.encode(Double.NaN))
        assertEquals("null", JsonEncoder.encode(Double.POSITIVE_INFINITY))
        assertEquals("null", JsonEncoder.encode(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `字符串转义`() {
        assertEquals("\"a\\\"b\"", JsonEncoder.encode("a\"b"))
        assertEquals("\"a\\\\b\"", JsonEncoder.encode("a\\b"))
        assertEquals("\"a\\nb\"", JsonEncoder.encode("a\nb"))
        assertEquals("\"a\\tb\"", JsonEncoder.encode("a\tb"))
        assertEquals("\"\\u0001\"", JsonEncoder.encode("\u0001"))
    }

    @Test
    fun `对象与数组编码`() {
        assertEquals("{}", JsonEncoder.encode(emptyMap<String, Any>()))
        assertEquals("[]", JsonEncoder.encode(emptyList<Any>()))
        assertEquals(
            """{"a":1,"b":"x"}""",
            JsonEncoder.encode(linkedMapOf("a" to 1, "b" to "x"))
        )
        assertEquals("[1,2,3]", JsonEncoder.encode(listOf(1, 2, 3)))
    }

    @Test
    fun `嵌套结构与数组类型`() {
        val payload = linkedMapOf<String, Any>(
            "name" to "服务器",
            "tps" to listOf("20.0", "19.9"),
            "nested" to linkedMapOf<String, Any?>("k" to null, "v" to arrayOf(1, 2)),
            "chars" to charArrayOf('a', 'b')
        )
        val json = JsonEncoder.encode(payload)

        assertEquals(
            """{"name":"服务器","tps":["20.0","19.9"],"nested":{"k":null,"v":[1,2]},"chars":"ab"}""",
            json
        )
    }

    @Test
    fun `未知类型退化为其字符串表示`() {
        val json = JsonEncoder.encode(mapOf("obj" to object {
            override fun toString(): String = "SENTINEL"
        }))
        assertEquals("""{"obj":"SENTINEL"}""", json)
    }

    @Test
    fun `data URL 原样保留`() {
        val url = "data:image/png;base64,AAAA+/="
        val json = JsonEncoder.encode(mapOf("avatarUrl" to url))
        assertTrue(json.contains(url), "data URL 不应被改写：$json")
    }
}
