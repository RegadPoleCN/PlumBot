// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal.config

import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File

/** 配置结构版本键；由 [ConfigMigrator] 自动维护。 */
internal const val CONFIG_VERSION_KEY = "config_version"

/** 当前配置结构版本；模板与迁移步骤必须与它一致。 */
internal const val CONFIG_VERSION = 2

/** 单步结构迁移：把 [fromVersion] 的配置升级到 [fromVersion] + 1。 */
internal interface ConfigMigration {
    val fromVersion: Int
    fun migrate(root: CommentedConfigurationNode): Boolean
}

object ConfigMigrator {

    /** 按版本顺序排列的迁移步骤；新增结构版本时在此追加。 */
    private val migrations: List<ConfigMigration> = listOf(LegacyConfigMigration)

    /**
     * 按 `config_version` 升级旧结构，再用内置模板补齐缺失节点。版本缺失视为 1；版本高于当前
     * 代码时不改写，避免旧插件把新配置降级。
     *
     * 2 版开发期曾用过 `feature.<功能>.pic` 结构，因此对已标记 2 但仍残留旧键的配置再跑一次
     * [LegacyConfigMigration] 归一化（版本号不变，新结构不会命中）。
     *
     * @return true 代表发生了迁移或补全并成功写回
     */
    fun completeMissingDefaults(localFile: File, defaultResourcePath: String): Boolean {
        if (!localFile.exists()) return false

        val defaultStream = javaClass.getResourceAsStream(defaultResourcePath) ?: return false
        val defaultLoader = YamlConfigurationLoader.builder()
            .source { defaultStream.bufferedReader() }
            .nodeStyle(NodeStyle.BLOCK)
            .build()
        val defaultRoot = defaultLoader.load()

        val localLoader = YamlConfigurationLoader.builder()
            .file(localFile)
            .nodeStyle(NodeStyle.BLOCK)
            .build()
        val localRoot = localLoader.load()

        var hasModified = false
        val isMainConfig = defaultResourcePath == "/config.yml"

        if (isMainConfig) {
            var version = localRoot.node(CONFIG_VERSION_KEY).int.takeIf { it > 0 } ?: 1

            // 配置比当前代码还新：不猜测结构，原样保留。
            if (version > CONFIG_VERSION) return false

            // 逐级升级旧结构；没有对应迁移步骤时放弃，避免留下半新半旧的配置。
            var migrated = false
            migrations.sortedBy { it.fromVersion }.forEach { migration ->
                if (version <= migration.fromVersion && migration.fromVersion < CONFIG_VERSION) {
                    if (migration.migrate(localRoot)) hasModified = true
                    version = migration.fromVersion + 1
                    migrated = true
                }
            }

            // 2 版开发期结构：版本号不变，仅做一次形状归一化。
            if (!migrated && version == CONFIG_VERSION && LegacyConfigMigration.hasLegacyLayout(localRoot)) {
                if (LegacyConfigMigration.migrate(localRoot)) hasModified = true
            }

            if (version != CONFIG_VERSION) return false
        }

        fun mergeNodes(defaultNode: CommentedConfigurationNode, localNode: CommentedConfigurationNode) {
            defaultNode.childrenMap().forEach { (key, childDefault) ->
                val childLocal = localNode.node(key)
                if (childLocal.virtual()) {
                    childLocal.set(childDefault.raw())
                    hasModified = true
                } else if (childDefault.isMap) {
                    mergeNodes(childDefault, childLocal)
                }
            }
        }

        mergeNodes(defaultRoot, localRoot)

        if (isMainConfig && localRoot.node(CONFIG_VERSION_KEY).int != CONFIG_VERSION) {
            localRoot.node(CONFIG_VERSION_KEY).set(CONFIG_VERSION)
            hasModified = true
        }

        if (hasModified) {
            localLoader.save(localRoot)
        }
        return hasModified
    }
}
