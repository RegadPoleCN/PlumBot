// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.api.platform.PlatformConfig
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * getDoubleOrNull 的读取语义：缺失/非数值给 null（区别于 getDouble 的 NaN/0.0 哨兵），
 * 显式配置的整数与浮点都按 Double 读回。
 */
class PlatformConfigDoubleTest {

    private fun configOf(yaml: String): YamlConfigurator {
        val node = YamlConfigurationLoader.builder()
            .source { StringReader(yaml).buffered() }
            .build()
            .load()
        return YamlConfigurator(node)
    }

    @Test
    fun `getDoubleOrNull returns configured values and null for missing keys`() {
        val yaml = "render:\n    scale: 1.5\n    integer_scale: 2\n"
        val config = configOf(yaml)
        assertEquals(1.5, config.getDoubleOrNull("render", "scale"))
        assertEquals(2.0, config.getDoubleOrNull("render", "integer_scale"))
        assertNull(config.getDoubleOrNull("render", "missing"))
        assertNull(config.getDoubleOrNull("nothing", "at", "all"))
    }

    @Test
    fun `interface default maps NaN sentinel to null`() {
        val stub = object : PlatformConfig {
            override fun getString(vararg path: String?): String? = null
            override fun getLong(vararg path: String?): Long = 0L
            override fun getInteger(vararg path: String?): Int = 0
            override fun getBoolean(vararg path: String?): Boolean = false
            override fun getLongList(vararg path: String?): List<Long> = emptyList()
            override fun getStringList(vararg path: String?): List<String?> = emptyList()
            override fun getSubConfig(vararg path: String?): PlatformConfig = this
        }
        assertNull(stub.getDoubleOrNull("any"), "未覆写 getDouble 的实现：NaN 哨兵应映射为 null")
    }
}