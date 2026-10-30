// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [JsonEncoder] 契约测试。
 *
 * 自持实现取代 Gson：消除「`:render` 在 `engine=awt` 时仍要求运行期存在 gson」的失败模式。
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
    fun `RenderCard 编码为对象而不是 toString`() {
        val card = RenderCard(
            text = "第一行\n第二行",
            title = "标题",
            data = linkedMapOf("state" to "CAPTURED", "durationMs" to 12L)
        )
        assertEquals(
            """{"text":"第一行\n第二行","title":"标题","data":{"state":"CAPTURED","durationMs":12}}""",
            JsonEncoder.encode(card)
        )
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

    @Test
    fun `二进制编码为 base64 而不是对象标识`() {
        val json = JsonEncoder.encode(mapOf("bytes" to byteArrayOf(1, 2, 3)))
        assertEquals("""{"bytes":"AQID"}""", json)
        assertTrue(!json.contains("[B@"), "ByteArray 不应退化成 toString：$json")
    }

    @Test
    fun `枚举编码为常量名`() {
        val json = JsonEncoder.encode(mapOf("engine" to TestEngine.PLAYWRIGHT))
        assertEquals("""{"engine":"PLAYWRIGHT"}""", json)
    }

    @Test
    fun `原生数组编码为 JSON 数组`() {
        val json = JsonEncoder.encode(
            linkedMapOf(
                "ints" to intArrayOf(1, 2),
                "longs" to longArrayOf(3),
                "bools" to booleanArrayOf(true, false)
            )
        )
        assertEquals("""{"ints":[1,2],"longs":[3],"bools":[true,false]}""", json)
    }

    private enum class TestEngine { AWT, PLAYWRIGHT }
}
