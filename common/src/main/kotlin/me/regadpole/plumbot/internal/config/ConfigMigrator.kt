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

import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.File

object ConfigMigrator {

    /**
     * 对比本地已存在的配置文件与内置默认模板，自动增量补全缺失的节点与注释
     * @return true 代表发现了缺失项并成功合并写回
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

        // 渲染门控迁移的关键时序：必须在 mergeNodes **之前**记录新键是否缺失，
        // 合并之后新键一律存在，那时再判就晚了（详见 RenderGateMigration 的说明）。
        val pendingGates = RenderGateMigration.pendingRoutes(localRoot)

        fun mergeNodes(defaultNode: CommentedConfigurationNode, localNode: CommentedConfigurationNode) {
            defaultNode.childrenMap().forEach { (key, childDefault) ->
                val childLocal = localNode.node(key)
                if (childLocal.virtual()) {
                    childLocal.set(childDefault.raw())
                    childDefault.comment()?.let { childLocal.comment(it) }
                    hasModified = true
                } else if (childDefault.isMap) {
                    mergeNodes(childDefault, childLocal)
                }
            }
        }

        mergeNodes(defaultRoot, localRoot)

        // 用旧键的**实际取值**（而非默认值）填充新键，保证升级前后行为完全一致
        if (RenderGateMigration.applyLegacyValues(localRoot, pendingGates)) {
            hasModified = true
        }

        if (hasModified) {
            localLoader.save(localRoot)
        }
        return hasModified
    }
}
