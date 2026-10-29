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

import me.regadpole.plumbot.testing.FakePlatformConfig
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `feature.message.console_echo` 的默认值契约：控制台回显必须**默认开**，玩家删掉这一行、
 * 或老配置没有这一行，都不能静默关掉；显式写 `false` 才关。
 */
class ConsoleEchoSettingTest {

    @Test
    fun `键缺失时默认开启`() {
        assertTrue(ConsoleEchoSetting.enabled(FakePlatformConfig()), "缺失必须回落 true，否则删掉一行就静默关了回显")
    }

    @Test
    fun `显式 false 关闭`() {
        val config = FakePlatformConfig(mapOf("feature.message.console_echo" to false))
        assertFalse(ConsoleEchoSetting.enabled(config))
    }

    @Test
    fun `显式 true 开启`() {
        val config = FakePlatformConfig(mapOf("feature.message.console_echo" to true))
        assertTrue(ConsoleEchoSetting.enabled(config))
    }

    @Test
    fun `字符串 false 也按关闭处理`() {
        val config = FakePlatformConfig(mapOf("feature.message.console_echo" to "false"))
        assertFalse(ConsoleEchoSetting.enabled(config), "沿用 getBoolean 的字符串语义")
    }

    @Test
    fun `内置模板默认开启`() {
        val text = requireNotNull(javaClass.classLoader.getResourceAsStream("config.yml")) { "内置 config.yml 不在 classpath" }
            .use { it.readBytes().decodeToString() }
        val file = Files.createTempFile("plumbot-console-echo", ".yml")
        Files.writeString(file, text)
        val config = YamlConfigurator(YamlConfigurationLoader.builder().path(file).build().load())

        assertTrue(ConsoleEchoSetting.enabled(config), "内置模板必须默认开启")
    }
}