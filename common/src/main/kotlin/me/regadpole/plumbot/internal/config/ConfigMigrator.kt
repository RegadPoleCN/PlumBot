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

        if (hasModified) {
            localLoader.save(localRoot)
        }
        return hasModified
    }
}
