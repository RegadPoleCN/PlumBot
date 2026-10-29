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
package me.regadpole.plumbot.config

import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 配置读取的**非变异**契约（用真实 YAML 节点，不是 FakeConfig）。configurate 的
 * `getBoolean` / `boolean` / `int` / `double` 是**带默认值拷贝**的读法：类型对不上就把默认值
 * **写回节点**——`render.engine: playwright` 读一次布尔就变成 `Boolean false`，之后 `getString`
 * 返回 `"false"`；缺失键也会被实体化，「没配」与「配成 false」再也分不开（三端曾因此齐报
 * 「未知渲染引擎 'false'」并全量回落 AWT）。这里把 `getBoolean` / `contains` / `rawTypeOf`
 * 的语义钉死：任何写回都会立刻让用例变红。
 */
class ConfigReadSemanticsTest {

    private fun yaml(text: String): YamlConfigurator {
        val file = Files.createTempFile("plumbot-config-read", ".yml")
        Files.writeString(file, text)
        return YamlConfigurator(YamlConfigurationLoader.builder().path(file).build().load())
    }

    private val sample = """
        render:
          engine: playwright
          outputs:
            status: true
        feature:
          remote_console:
            enable: false
        flags:
          quoted_true: 'true'
          numeric_one: 1
          numeric_zero: 0
          ambiguous: 'yes'
    """.trimIndent()

    @Test
    fun `getBoolean 读字符串节点不写回原值`() {
        val config = yaml(sample)

        assertFalse(config.getBoolean("render", "engine"), "字符串不是布尔，返回 false 即可但不许改写")
        assertEquals("playwright", config.getString("render", "engine"), "配置被就地改写成了 false")
        assertEquals("String", config.rawTypeOf("render", "engine"))
        assertTrue(config.contains("render", "engine"))
    }

    @Test
    fun `getBoolean 读缺失键不实体化节点`() {
        val config = yaml(sample)

        assertFalse(config.getBoolean("render", "views", "status", "engine"))
        assertFalse(
            config.contains("render", "views", "status", "engine"),
            "缺失键被 getBoolean 实体化了：之后 getString 会返回 \"false\" 而不是 null"
        )
        assertNull(config.getString("render", "views", "status", "engine"))
        assertEquals("missing", config.rawTypeOf("render", "views", "status", "engine"))
    }

    @Test
    fun `getBoolean 按原始标量类型解析`() {
        val config = yaml(sample)

        assertTrue(config.getBoolean("render", "outputs", "status"), "YAML true")
        assertFalse(config.getBoolean("feature", "remote_console", "enable"), "YAML false")
        assertTrue(config.getBoolean("flags", "quoted_true"), "带引号的 \"true\" 也是真")
        assertTrue(config.getBoolean("flags", "numeric_one"), "非零数字按真处理")
        assertFalse(config.getBoolean("flags", "numeric_zero"), "0 按假处理")
        assertFalse(config.getBoolean("flags", "ambiguous"), "YAML 的 yes 是字符串，不猜")
        assertFalse(config.getBoolean("nope", "nothing"), "缺失即 false，且不得落节点")
    }

    @Test
    fun `getString 仍能把布尔与数字节点转成文本`() {
        val config = yaml(sample)

        assertEquals("true", config.getString("render", "outputs", "status"))
        assertEquals("Boolean", config.rawTypeOf("render", "outputs", "status"))
        assertEquals("false", config.getString("feature", "remote_console", "enable"))
        assertEquals("1", config.getString("flags", "numeric_one"))
    }

    @Test
    fun `连续读取布尔与字符串保持稳定`() {
        val config = yaml(sample)

        repeat(3) {
            assertFalse(config.getBoolean("render", "engine"))
            assertEquals("playwright", config.getString("render", "engine"))
            assertFalse(config.getBoolean("render", "views", "status", "engine"))
        }
        assertEquals("String", config.rawTypeOf("render", "engine"))
        assertEquals("missing", config.rawTypeOf("render", "views", "status", "engine"))
    }
}
