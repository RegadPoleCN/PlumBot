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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 旧版 `config.yml` 结构迁移的回归测试。
 *
 * 升级前后的可见行为必须一致：
 * - `feature.img.*` 的值不能丢（引擎、字体、限额、头像、浏览器配置）；
 * - 旧的双重出图开关合并后不能把开着的图关掉，也不能把关着的图重新打开；
 * - 指令前缀与触发词换位置后仍可用；
 * - 2 版开发期结构只归一化，不改版本号。
 */
class LegacyConfigMigrationTest {

    private fun tempConfig(content: String, name: String = "config.yml"): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "plumbot-migrate-${System.nanoTime()}")
        dir.mkdirs()
        return File(dir, name).apply { writeText(content) }
    }

    private fun load(file: File) = YamlConfigurationLoader.builder()
        .file(file)
        .nodeStyle(NodeStyle.BLOCK)
        .build()
        .load()

    @Test
    fun `旧 feature_img 整体迁移到 render 并保留取值`() {
        val file = tempConfig(
            """
            feature:
                status:
                    pic: false
                avatarCard:
                    pic: false
                img:
                    engine: "playwright"
                    file: "old.ttf"
                    text:
                        base: "#111111"
                    limits:
                        display_text_max: 64
                    playwright:
                        scale_factor: 2.0
                    views:
                        status:
                            engine: "playwright"
                            timeout_ms: 9000
                            pic: true
                            scale_factor: 3.0
            """.trimIndent()
        )

        assertTrue(ConfigMigrator.completeMissingDefaults(file, "/config.yml"))

        val root = load(file)
        assertEquals(CONFIG_VERSION, root.node(CONFIG_VERSION_KEY).int)
        assertEquals("playwright", root.node("render", "engine").string)
        assertEquals("old.ttf", root.node("render", "font").string)
        assertEquals("#111111", root.node("render", "text", "base").string)
        assertEquals(64, root.node("render", "limits", "display_text_max").int)
        assertEquals(
            2.0,
            root.node("render", "playwright", "scale_factor").double,
            "全局缩放不能被当成逐路由覆盖丢掉"
        )
        assertEquals("playwright", root.node("render", "views", "status", "engine").string)
        assertEquals(9000, root.node("render", "views", "status", "timeout_ms").int)
        assertEquals(true, root.node("render", "outputs", "status").boolean, "旧 views 开关为 true 时图片必须仍然打开")
        assertEquals(false, root.node("render", "outputs", "avatar").boolean, "旧头像卡关闭状态必须保留")

        assertTrue(root.node("feature", "img").virtual(), "feature.img 迁移后必须删除")
        assertTrue(root.node("feature", "status", "pic").virtual(), "旧功能开关已并入 render.outputs")
        assertTrue(root.node("feature", "avatarCard").virtual(), "avatarCard 已并入 render.outputs.avatar")
        assertTrue(root.node("render", "views", "status", "pic").virtual(), "逐路由 pic 已废弃")
        assertTrue(root.node("render", "views", "status", "scale_factor").virtual(), "逐路由缩放已废弃")
    }

    @Test
    fun `旧开关都关闭时不会被模板默认值重新打开`() {
        val file = tempConfig(
            """
            feature:
                list:
                    pic: false
                img:
                    views:
                        playerList:
                            pic: false
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(false, root.node("render", "outputs", "list").boolean, "迁移应保留旧版的关闭状态")
        assertTrue(root.node("feature", "list", "pic").virtual())
        assertTrue(root.node("render", "views", "playerList", "pic").virtual())
    }

    @Test
    fun `旧 views 开关为 true 时合并后的功能开关必须为 true`() {
        val file = tempConfig(
            """
            feature:
                status:
                    pic: false
                img:
                    views:
                        status:
                            pic: true
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(true, root.node("render", "outputs", "status").boolean, "任一侧为 true 都不能丢图")
        assertTrue(root.node("feature", "status", "pic").virtual(), "旧功能开关必须清理")
        assertTrue(
            root.node("render", "views", "status", "pic").virtual(),
            "旧路由开关必须清掉，避免两处值再次不一致"
        )
    }

    @Test
    fun `旧功能开关为 true 且 views 为 false 时保持开启`() {
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
        assertEquals(true, root.node("render", "outputs", "status").boolean, "旧版兜底逻辑为 views || 功能开关")
    }

    @Test
    fun `共用门控的多个路由只写一个功能开关`() {
        val file = tempConfig(
            """
            feature:
                img:
                    views:
                        whitelist:
                            pic: true
                        bindList:
                            pic: false
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(true, root.node("render", "outputs", "bind").boolean)
        assertTrue(root.node("feature", "bind", "pic").virtual())
        assertTrue(root.node("render", "views", "whitelist", "pic").virtual())
        assertTrue(root.node("render", "views", "bindList", "pic").virtual())
    }

    @Test
    fun `旧头像卡开关迁移到 render_outputs_avatar`() {
        val file = tempConfig(
            """
            feature:
                avatarCard:
                    pic: false
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(false, root.node("render", "outputs", "avatar").boolean)
        assertTrue(root.node("feature", "avatarCard").virtual())
    }

    @Test
    fun `2 版开发期头像开关迁移到 render_outputs_avatar`() {
        val file = tempConfig(
            """
            config_version: 2
            feature:
                avatar:
                    pic: false
            """.trimIndent()
        )

        assertTrue(ConfigMigrator.completeMissingDefaults(file, "/config.yml"))

        val root = load(file)
        assertEquals(2, root.node(CONFIG_VERSION_KEY).int, "归一化不能改变版本号")
        assertEquals(false, root.node("render", "outputs", "avatar").boolean)
        assertTrue(root.node("feature", "avatar", "pic").virtual())
    }

    @Test
    fun `指令前缀与触发词迁移到 command 段`() {
        val file = tempConfig(
            """
            feature:
                cmdPrefix: "!"
            keys:
                list:
                    - "tps"
                avatar:
                    - "tou"
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals("!", root.node("command", "prefix").string)
        assertEquals(listOf("tps"), root.node("command", "keys", "list").childrenList().map { it.string })
        assertEquals(listOf("tou"), root.node("command", "keys", "avatar").childrenList().map { it.string })
        assertTrue(root.node("feature", "cmdPrefix").virtual())
        assertTrue(root.node("keys").virtual())
    }

    @Test
    fun `2 版开发期旧结构会被归一化且版本仍为 2`() {
        val file = tempConfig(
            """
            config_version: 2
            feature:
                status:
                    pic: true
                img:
                    engine: "playwright"
                    views:
                        status:
                            pic: true
            """.trimIndent()
        )

        assertTrue(ConfigMigrator.completeMissingDefaults(file, "/config.yml"))

        val root = load(file)
        assertEquals(2, root.node(CONFIG_VERSION_KEY).int)
        assertEquals(true, root.node("render", "outputs", "status").boolean)
        assertEquals("playwright", root.node("render", "engine").string)
        assertTrue(root.node("feature", "img").virtual())
        assertTrue(root.node("feature", "status", "pic").virtual())
    }

    @Test
    fun `2 版新结构不会被归一化改写`() {
        val file = tempConfig(
            """
            config_version: 2
            render:
                outputs:
                    status: false
                    list: true
            feature:
                status:
                    enable: true
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(2, root.node(CONFIG_VERSION_KEY).int)
        assertEquals(false, root.node("render", "outputs", "status").boolean)
        assertEquals(true, root.node("render", "outputs", "list").boolean, "用户显式设置的新开关不能被模板覆盖")
        assertTrue(root.node("feature", "status", "pic").virtual())
    }

    @Test
    fun `已存在的 render_outputs 优先于旧 pic`() {
        val file = tempConfig(
            """
            config_version: 2
            render:
                outputs:
                    status: false
            feature:
                status:
                    pic: true
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(false, root.node("render", "outputs", "status").boolean, "新键优先，旧键只负责清理")
        assertTrue(root.node("feature", "status", "pic").virtual())
    }

    @Test
    fun `旧 help 与 kick 占位段被删除`() {
        val file = tempConfig(
            """
            feature:
                help:
                    pic: true
                kick:
                    pic: false
            """.trimIndent()
        )

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertEquals(true, root.node("render", "outputs", "help").boolean)
        assertEquals(false, root.node("render", "outputs", "kick").boolean)
        assertTrue(root.node("feature", "help").virtual(), "help 只是旧占位段")
        assertTrue(root.node("feature", "kick").virtual(), "kick 只是旧占位段")
    }

    @Test
    fun `未来版本的配置不会被降级改写`() {
        val file = tempConfig(
            """
            config_version: 99
            server:
                name: future
            """.trimIndent()
        )

        assertFalse(ConfigMigrator.completeMissingDefaults(file, "/config.yml"))

        val root = load(file)
        assertEquals(99, root.node(CONFIG_VERSION_KEY).int)
        assertEquals("future", root.node("server", "name").string)
        assertTrue(root.node("render").virtual(), "未来版本不应被旧模板合并")
    }

    @Test
    fun `未使用的 ver 键会被删除并补齐默认值`() {
        val file = tempConfig("ver: \"1.0\"\nserver:\n    name: lobby")

        ConfigMigrator.completeMissingDefaults(file, "/config.yml")

        val root = load(file)
        assertTrue(root.node("ver").virtual())
        assertEquals(CONFIG_VERSION, root.node(CONFIG_VERSION_KEY).int)
        assertEquals("lobby", root.node("server", "name").string)
        assertEquals("auto", root.node("render", "engine").string, "模板默认 engine=auto（按路由选首选引擎）")
        assertEquals("/", root.node("command", "prefix").string)
        assertEquals(true, root.node("render", "outputs", "avatar").boolean)
        assertEquals(true, root.node("render", "outputs", "status").boolean)
    }

    @Test
    fun `迁移可重复执行且第二次不再改动`() {
        val file = tempConfig(
            """
            feature:
                img:
                    engine: "playwright"
                    views:
                        status:
                            pic: true
            keys:
                list:
                    - "tps"
            """.trimIndent()
        )

        assertTrue(ConfigMigrator.completeMissingDefaults(file, "/config.yml"), "首次应迁移")
        assertFalse(ConfigMigrator.completeMissingDefaults(file, "/config.yml"), "第二次不应再改动")
    }

    @Test
    fun `messages 模板不参与 config 结构迁移`() {
        val file = tempConfig("ver: \"1.0\"\nhelp:\n    - \"x\"", name = "messages.yml")

        ConfigMigrator.completeMissingDefaults(file, "/messages.yml")

        val root = load(file)
        assertEquals("1.0", root.node("ver").string, "messages.yml 没有结构迁移，ver 应原样保留")
    }
}
