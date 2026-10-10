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

import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 渲染门控迁移的**回归测试**（基线验收：升级后行为不变）。
 *
 * 背景：`PlatformConfig.getBoolean` 返回非空 Boolean，无法区分"键缺失"与"显式 false"。
 * 若直接改读 `feature.img.views.<route>.pic` 而不做迁移，所有现存用户的出图会被**静默关闭**。
 * 本测试锁死"以旧键实际取值迁移"这一行为。
 */
class RenderGateMigrationTest {

    private fun tempConfig(content: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "plumbot-migrate-${System.nanoTime()}")
        dir.mkdirs()
        return File(dir, "config.yml").apply { writeText(content) }
    }

    private fun load(file: File) = YamlConfigurationLoader.builder()
        .file(file)
        .nodeStyle(NodeStyle.BLOCK)
        .build()
        .load()

    @Test
    fun `旧键为 true 时迁移出新键 true`() {
        val file = tempConfig(
            """
            feature:
                status:
                    pic: true
                bind:
                    pic: true
            """.trimIndent()
        )

        assertTrue(ConfigMigrator.completeMissingDefaults(file, "/config.yml"), "应识别出缺失项并合并")

        val root = load(file)
        assertEquals(true, root.node("feature", "img", "views", "status", "pic").boolean)
        // whitelist / bindList 的旧门控同为 bind
        assertEquals(true, root.node("feature", "img", "views", "whitelist", "pic").boolean)
        assertEquals(true, root.node("feature", "img", "views", "bindList", "pic").boolean)
    }

    @Test
    fun `旧键为 false 时迁移出新键 false`() {
        val file = tempConfig(
            """
            feature:
                status:
                    pic: false
            """.trimIndent()
        )
        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(false, root.node("feature", "img", "views", "status", "pic").boolean)
    }

    @Test
    fun `已显式配置的新键不会被旧键覆盖`() {
        val file = tempConfig(
            """
            feature:
                status:
                    pic: true
                img:
                    views:
                        status:
                            pic: false
            """.trimIndent()
        )
        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(
            false,
            root.node("feature", "img", "views", "status", "pic").boolean,
            "用户显式写入的新键必须优先"
        )
    }

    @Test
    fun `旧键也不存在时保留默认模板值`() {
        val file = tempConfig("feature:\n    bind:\n        maxNum: 3")
        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        // 配置模板中的默认值为 false（保守：出图默认关闭）
        assertEquals(false, root.node("feature", "img", "views", "status", "pic").boolean)
        // 用户原有配置不得被破坏
        assertEquals(3, root.node("feature", "bind", "maxNum").int)
    }

    @Test
    fun `无独立门控的 route 不生成新键`() {
        val file = tempConfig("feature:\n    status:\n        pic: true")
        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        // text / hytaleAvatar 的 legacyGate 为 null，由调用方决定是否出图
        assertTrue(
            root.node("feature", "img", "views", "text", "pic").virtual(),
            "text 路由不应被写入门控键"
        )
        assertTrue(
            root.node("feature", "img", "views", "hytaleAvatar", "pic").virtual(),
            "hytaleAvatar 路由不应被写入门控键"
        )
    }
}
