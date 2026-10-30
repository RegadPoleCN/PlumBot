// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 内置配置模板的"键必须存在"护栏。
 *
 * `PlatformConfig.getBoolean` 返回**非空 Boolean**，键缺失一律读成 `false`：模板少写一个
 * `enable` 键，该功能就在用户侧静默失效且没有任何日志（`%cmdprefix%avatar` 曾因此点了没反应）。
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
    fun `配置结构版本与代码常量一致`() {
        val root = load("/config.yml")
        assertEquals(
            CONFIG_VERSION,
            root.node(CONFIG_VERSION_KEY).int,
            "模板的 config_version 必须与代码里的当前版本一致，否则新安装的配置会被误判"
        )
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
    fun `头像指令的开关与出图开关是两个独立的键`() {
        val root = load("/config.yml")

        assertFalse(
            root.node("feature", "avatar", "enable").virtual(),
            "指令开关必须存在，否则 %cmdprefix%avatar 静默失效"
        )
        assertFalse(
            root.node("render", "outputs", "avatar").virtual(),
            "头像出图开关必须存在，否则头像指令永远回纯文本"
        )
    }

    /**
     * 出图开关集中且只保留在 `render.outputs.<功能>`，旧结构不得回流：旧版
     * `feature.<功能>.pic` 与 `feature.img.views.<route>.pic` 并存且后者覆盖前者，排查时极易误判。
     */
    @Test
    fun `出图开关集中定义且旧结构不回流`() {
        val root = load("/config.yml")
        val outputs = root.node("render", "outputs")

        assertEquals(
            setOf(
                "load", "message", "list", "bind", "joinAndLeave", "death",
                "advancement", "status", "remote_console", "help", "kick", "avatar", "text"
            ),
            outputs.childrenMap().keys.map { it.toString() }.toSet(),
            "render.outputs 必须与 RenderRoute.featureGate 一一对应，不能多也不能少"
        )

        // status / remote_console / help 默认开图（"管理员要看的结论"，关图会被当成渲染坏了）；
        // message 与 death/advancement/joinAndLeave 属高频路径，默认关图；load/kick 出现即事故，
        // 纯文本更易检索。Velocity/Hytale 也没有死亡/成就事件。
        val expected = mapOf(
            "load" to false,
            "message" to false,
            "list" to true,
            "bind" to true,
            "joinAndLeave" to false,
            "death" to false,
            "advancement" to false,
            "status" to true,
            "remote_console" to true,
            "help" to true,
            "kick" to false,
            "avatar" to true,
            "text" to false
        )
        assertEquals(
            expected,
            outputs.childrenMap().mapKeys { it.key.toString() }.mapValues { it.value.boolean },
            "模板默认出图状态不能让升级后的用户意外多出或少掉图片"
        )

        RenderRouteOutputGates.ALL.forEach { name ->
            assertTrue(
                root.node("feature", name, "pic").virtual(),
                "feature.$name.pic 已废弃，出图开关只应在 render.outputs.$name"
            )
        }
        listOf("help", "kick").forEach { name ->
            assertTrue(root.node("feature", name).virtual(), "feature.$name 只是旧占位段，应已删除")
        }
        assertTrue(root.node("feature", "avatarCard").virtual(), "头像出图开关已并入 render.outputs.avatar")
        assertTrue(root.node("feature", "img").virtual(), "渲染配置已移到顶层 render，feature.img 不应再出现")
        assertTrue(root.node("feature", "cmdPrefix").virtual(), "指令前缀已移到 command.prefix")
        assertTrue(root.node("keys").virtual(), "指令触发词已移到 command.keys")
        assertTrue(root.node("ver").virtual(), "ver 已由 config_version 取代")
        assertFalse(root.node("command", "prefix").virtual(), "command.prefix 必须存在")
        assertFalse(root.node("render", "text", "base").virtual(), "render.text 必须存在")
    }

    /** 与 `RenderRoute.featureGate` 的并集保持同步；测试侧独立列出，避免复制生产实现。 */
    private object RenderRouteOutputGates {
        val ALL = listOf(
            "load", "message", "list", "bind", "joinAndLeave", "death",
            "advancement", "status", "remote_console", "help", "kick", "avatar"
        )
    }
}
