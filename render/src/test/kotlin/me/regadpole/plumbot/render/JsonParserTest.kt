// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.Test
import me.regadpole.plumbot.render.support.JsonParser

class JsonParserTest {

    @Test
    fun `解析嵌套对象与数组`() {
        val root = JsonParser.parse(
            """{"id":"069a79f4","name":"Notch","properties":[{"name":"textures","value":"eyJ4IjoxfQ=="}]}"""
        )
        assertEquals("069a79f4", JsonParser.path(root, "id"))
        assertEquals("Notch", JsonParser.path(root, "name"))
        assertEquals("textures", JsonParser.path(root, "properties", 0, "name"))
        assertEquals("eyJ4IjoxfQ==", JsonParser.path(root, "properties", 0, "value"))
    }

    @Test
    fun `解析正版皮肤响应的完整路径`() {
        // sessionserver 响应里 value 是 base64，解码后是第二段 JSON
        val decoded = """{"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/abc"}}}"""
        val root = JsonParser.parse(decoded)
        assertEquals(
            "http://textures.minecraft.net/texture/abc",
            JsonParser.path(root, "textures", "SKIN", "url")
        )
    }

    @Test
    fun `解析标量与空白`() {
        assertEquals(1.0, JsonParser.parse(" 1 "))
        assertEquals(true, JsonParser.parse("true"))
        assertEquals(false, JsonParser.parse("false"))
        assertNull(JsonParser.parse("null"))
        assertEquals("hi", JsonParser.parse("""  "hi"  """))
        assertEquals(emptyList<Any?>(), JsonParser.parse("[]"))
        assertEquals(emptyMap<String, Any?>(), JsonParser.parse("{}"))
    }

    @Test
    fun `解析转义字符`() {
        val root = JsonParser.parse("""{"s":"a\"b\\c\nd\u0041"}""")
        assertEquals("a\"b\\c\ndA", JsonParser.path(root, "s"))
    }

    @Test
    fun `负数与小数`() {
        val root = JsonParser.parse("""{"a":-1.5,"b":1e3}""")
        assertEquals(-1.5, JsonParser.path(root, "a"))
        assertEquals(1000.0, JsonParser.path(root, "b"))
    }

    @Test
    fun `路径取不到时返回 null 而不是抛异常`() {
        val root = JsonParser.parse("""{"a":1}""")
        assertNull(JsonParser.path(root, "missing"))
        assertNull(JsonParser.path(root, "a", "b"))
        assertNull(JsonParser.path(root, "a", 0))
        assertNull(JsonParser.path(null, "a"))
    }

    @Test
    fun `非法输入抛出异常`() {
        assertFailsWith<IllegalArgumentException> { JsonParser.parse("{") }
        assertFailsWith<IllegalArgumentException> { JsonParser.parse("""{"a":1,}""") }
        assertFailsWith<IllegalArgumentException> { JsonParser.parse("""{"a"}""") }
        assertFailsWith<IllegalArgumentException> { JsonParser.parse("""{"a":1} extra""") }
        assertFailsWith<IllegalArgumentException> { JsonParser.parse("") }
    }
}
