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

import me.regadpole.plumbot.render.RenderRoute
import org.spongepowered.configurate.CommentedConfigurationNode

/**
 * 旧版 `config.yml` → `config_version = 2` 的就地迁移。
 *
 * 由 [ConfigMigrator] 在补齐默认值之前按版本调用；本步骤负责 1 → 2。
 * 2 版开发期曾短暂把出图开关放在 `feature.<功能>.pic`，因此同一套逻辑也用于
 * 把这种未发布的结构归一化到最终布局，且**不改变版本号**。
 *
 * 处理五类历史遗留：
 * 1. `feature.img.*` → `render.*`（含 `file` → `font`）；
 * 2. 出图开关散落在 `feature.img.views.<route>.pic` 与 `feature.<功能>.pic` 两处
 *    → 合并为唯一的 `render.outputs.<功能>`（任一为 true 即保留出图）；
 * 3. `feature.avatarCard.pic` / 2 版开发期的 `feature.avatar.pic` → `render.outputs.avatar`；
 * 4. `feature.cmdPrefix` / 顶层 `keys` → `command.prefix` / `command.keys`；
 * 5. 删除 `feature.help` / `feature.kick` 等只承载出图开关的伪功能段。
 *
 * 迁移只改写旧结构；新键的默认值由模板合并补齐，因此升级后仍保留原来的行为。
 * 已存在的新键优先，避免覆盖用户在当前版本中显式设置的值。
 */
internal object LegacyConfigMigration : ConfigMigration {

    override val fromVersion: Int = 1

    private const val FEATURE = "feature"
    private const val IMG = "img"
    private const val RENDER = "render"
    private const val OUTPUTS = "outputs"
    private const val VIEWS = "views"
    private const val PIC = "pic"
    private const val SCALE_FACTOR = "scale_factor"
    private const val KEYS = "keys"
    private const val COMMAND = "command"
    private const val AVATAR = "avatar"
    private const val AVATAR_CARD = "avatarCard"
    private const val CMD_PREFIX = "cmdPrefix"
    private const val HELP = "help"
    private const val KICK = "kick"
    private const val VER = "ver"

    /**
     * 判断配置里是否还残留旧结构。
     *
     * `ConfigMigrator` 用它决定是否对已经标记为 2 版的配置执行兼容归一化；
     * 新结构不应命中任何一项。
     */
    internal fun hasLegacyLayout(root: CommentedConfigurationNode): Boolean {
        if (!root.node(FEATURE, IMG).virtual()) return true
        if (!root.node(FEATURE, AVATAR_CARD).virtual()) return true
        if (!root.node(FEATURE, CMD_PREFIX).virtual()) return true
        if (!root.node(KEYS).virtual()) return true
        if (!root.node(VER).virtual()) return true
        if (!root.node(FEATURE, HELP).virtual() || !root.node(FEATURE, KICK).virtual()) return true

        return RenderRoute.entries
            .mapNotNull { it.featureGate }
            .distinct()
            .any { !root.node(FEATURE, it, PIC).virtual() }
    }

    /**
     * @return true 表示配置文件被改写（调用方据此决定是否落盘）
     */
    override fun migrate(root: CommentedConfigurationNode): Boolean {
        var modified = false

        // 必须在移除 feature.img / feature.<功能>.pic 之前算清旧版每个功能最终是否出图。
        val imageSwitches = legacyImageSwitches(root)

        if (migrateRenderSection(root)) modified = true
        if (applyImageSwitches(root, imageSwitches)) modified = true
        if (removeLegacyFeatureNodes(root)) modified = true
        if (migrateCommandSection(root)) modified = true
        if (removeNode(root.node(VER))) modified = true

        return modified
    }

    /**
     * 旧版每个功能的实际出图状态：`views.<route>.pic`，或回落到
     * `feature.<功能>.pic`（与原门控逻辑一致；两者都缺失时不生成新键，
     * 让模板默认值决定）。
     *
     * 头像没有视图路由：1 版由 `avatarCard.pic` 控制，2 版开发期由
     * `feature.avatar.pic` 控制，两者都按旧值读取。
     */
    private fun legacyImageSwitches(root: CommentedConfigurationNode): Map<String, Boolean> {
        val switches = mutableMapOf<String, Boolean>()

        RenderRoute.entries.forEach { route ->
            val gate = route.featureGate ?: return@forEach
            val legacyNodes = buildList {
                add(root.node(FEATURE, gate, PIC))
                if (route == RenderRoute.HYTALE_AVATAR) {
                    add(root.node(FEATURE, AVATAR_CARD, PIC))
                } else {
                    add(root.node(FEATURE, IMG, VIEWS, route.route, PIC))
                }
            }.filterNot { it.virtual() }

            if (legacyNodes.isEmpty()) return@forEach
            val enabled = legacyNodes.any { it.boolean }
            switches[gate] = switches[gate] == true || enabled
        }

        return switches
    }

    /**
     * `feature.img.*` → `render.*`。
     *
     * 跳过 `pic`（已并入 `render.outputs`）与逐路由 `scale_factor`
     * （已废弃，只有全局值生效）。目标已存在的键优先，不覆盖新结构。
     */
    private fun migrateRenderSection(root: CommentedConfigurationNode): Boolean {
        val old = root.node(FEATURE, IMG)
        if (old.virtual()) return false

        copyLeaves(old, root.node(RENDER), rename = { if (it == "file") "font" else it })
        removeNode(old)
        return true
    }

    private fun copyLeaves(
        from: CommentedConfigurationNode,
        to: CommentedConfigurationNode,
        skipRouteScale: Boolean = false,
        rename: (String) -> String = { it }
    ): Boolean {
        var copied = false
        from.childrenMap().forEach { (key, child) ->
            val name = rename(key.toString())
            if (name == PIC) return@forEach
            // 只丢弃 views.<route>.scale_factor；playwright.scale_factor 是全局值，必须保留。
            if (skipRouteScale && name == SCALE_FACTOR) return@forEach
            val target = to.node(name)
            if (child.isMap) {
                if (copyLeaves(child, target, skipRouteScale || name == VIEWS) { it }) copied = true
            } else if (target.virtual()) {
                target.set(child.raw())
                copied = true
            }
        }
        return copied
    }

    /**
     * 把旧版实际状态写成唯一的功能开关。
     *
     * 即使结果是 false 也要落键，避免模板默认值把它打开；目标已存在时保留新值。
     */
    private fun applyImageSwitches(
        root: CommentedConfigurationNode,
        switches: Map<String, Boolean>
    ): Boolean {
        var modified = false
        switches.forEach { (gate, enabled) ->
            val node = root.node(RENDER, OUTPUTS, gate)
            if (node.virtual()) {
                node.set(enabled)
                modified = true
            }
        }
        return modified
    }

    /** 删除旧版功能段里的 `pic`，以及只承载出图开关的 help / kick / avatarCard。 */
    private fun removeLegacyFeatureNodes(root: CommentedConfigurationNode): Boolean {
        var modified = false

        RenderRoute.entries
            .mapNotNull { it.featureGate }
            .distinct()
            .forEach { gate ->
                if (removeNode(root.node(FEATURE, gate, PIC))) modified = true
            }

        listOf(HELP, KICK).forEach { name ->
            val node = root.node(FEATURE, name)
            if (!node.virtual() && node.childrenMap().isEmpty()) {
                if (removeNode(node)) modified = true
            }
        }

        if (removeNode(root.node(FEATURE, AVATAR_CARD))) modified = true
        return modified
    }

    private fun migrateCommandSection(root: CommentedConfigurationNode): Boolean {
        var modified = false

        val oldPrefix = root.node(FEATURE, CMD_PREFIX)
        if (!oldPrefix.virtual()) {
            val target = root.node(COMMAND, "prefix")
            if (target.virtual()) target.set(oldPrefix.raw())
            removeNode(oldPrefix)
            modified = true
        }

        val oldKeys = root.node(KEYS)
        if (!oldKeys.virtual()) {
            val newKeys = root.node(COMMAND, KEYS)
            oldKeys.childrenMap().forEach { (key, child) ->
                val target = newKeys.node(key)
                if (target.virtual()) target.set(child.raw())
            }
            removeNode(oldKeys)
            modified = true
        }

        return modified
    }

    /** 清空取值与注释，避免 YAML 里留下孤立注释。 */
    private fun removeNode(node: CommentedConfigurationNode): Boolean {
        if (node.virtual()) return false
        node.set(null)
        node.comment(null)
        return true
    }
}
