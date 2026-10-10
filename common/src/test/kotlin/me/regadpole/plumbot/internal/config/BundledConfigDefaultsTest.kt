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

package me.regadpole.plumbot.internal.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 内置配置模板的"键必须存在"护栏。
 *
 * 背景：`PlatformConfig.getBoolean` 返回**非空 Boolean**，键缺失一律读成 `false`。
 * 于是"模板里少写一个 `enable` 键"= 该功能在用户侧静默失效，且没有任何日志。
 * 实测踩到过：`feature.avatar.enable` 缺失使 `%cmdprefix%avatar` 存在但毫无反应。
 */
class BundledConfigDefaultsTest {

    private fun load(resource: String): ConfigurationNode {
        val url = requireNotNull(javaClass.getResource(resource)) { "找不到资源 $resource" }
        return YamlConfigurationLoader.builder().url(url).build().load()
    }

    @Test
    fun `每个命令功能的 enable 键都存在于内置模板`() {
        val root = load("/config.yml")
        // 与 DefaultBotHandler 的命令注册一一对应（新增命令时必须同步）
        val commandFeatures = listOf(
            "list", "bind", "status", "remote_console", "avatar"
        )
        // 事件/生命周期类门控
        val eventFeatures = listOf(
            "load", "message", "joinAndLeave", "death", "advancement"
        )

        (commandFeatures + eventFeatures).forEach { name ->
            assertFalse(
                root.node("feature", name, "enable").virtual(),
                "config.yml 缺少 feature.$name.enable：缺失会被读成 false，对应功能静默失效"
            )
        }
    }

    @Test
    fun `帮助模板列出 avatar 指令且令牌可被解析`() {
        val root = load("/messages.yml")
        val lines = root.node("help").childrenList().map { it.string.orEmpty() }

        assertTrue(lines.any { it.contains("\$avatar") }, "帮助模板缺少 \$avatar 行")
        assertTrue(
            lines.any { it.contains("\$status") } && lines.any { it.contains("\$remote_console") },
            "帮助模板应完整列出所有指令（含 status 与 remote_console）"
        )
    }

    @Test
    fun `头像指令的开关与出图门控是两个独立的键`() {
        val root = load("/config.yml")

        assertTrue(!root.node("feature", "avatar", "enable").virtual(), "指令开关必须存在")
        assertTrue(!root.node("feature", "avatarCard", "pic").virtual(), "出图门控必须存在")
    }
}
