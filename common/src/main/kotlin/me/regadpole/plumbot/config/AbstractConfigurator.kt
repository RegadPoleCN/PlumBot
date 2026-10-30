// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.PlumBot
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.AbstractConfigurationLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

/**
 * configurate 配置读取基类，实现平台无关的 `PlatformConfig`。
 *
 * 所有读取保持**纯读**：不把默认值写回节点，也不把缺失键实体化，
 * 否则「没配」与「配成 false」无法区分，还会就地改写用户写的值。
 */
abstract class AbstractConfigurator<C : AbstractConfigurator<C>> protected constructor(
    protected val config: ConfigurationNode
) : me.regadpole.plumbot.api.platform.PlatformConfig {

    protected abstract fun createConfigurator(node: ConfigurationNode): C
    protected abstract fun loaderBuilder(): AbstractConfigurationLoader.Builder<*, *>

    private fun resolveNode(vararg nodePath: String?): ConfigurationNode {
        val resolvedPath = nodePath.flatMap { path -> path?.split('.') ?: listOf(null) }.toTypedArray()
        return config.node(*resolvedPath)
    }

    fun getNode(): ConfigurationNode {
        return config
    }

    fun getNode(vararg nodePath: String?): ConfigurationNode {
        return resolveNode(*nodePath)
    }

    /** 配置节点是否存在（未缺失）。适配层（如 :bukkit）不依赖 configurate，不能直接调
     *  [getNode]，否则会报 "Cannot access class ConfigurationNode" 编译错误。 */
    override fun contains(vararg nodePath: String?): Boolean {
        return !resolveNode(*nodePath).virtual()
    }

    /** 原始标量类型名（诊断用）：缺失与显式 null 分开报告，doctor 可直出「类型错位」。
     *  读的是 `raw()`，不会像 `boolean`/`int`/`double` 属性那样把默认值写回节点。 */
    override fun rawTypeOf(vararg nodePath: String?): String? {
        val node = resolveNode(*nodePath)
        if (node.virtual()) return "missing"
        val raw = node.raw() ?: return "null"
        return raw.javaClass.simpleName
    }

    /**
     * 读取数值节点；缺失或写成非数字时返回 null。
     * 不能用 [getInteger]/[getDouble]：configurate 对两者一律返回 0/0.0，调用方无法区分
     * "用户显式写 0"和"根本没配"，会把漏配静默当成显式关闭。
     */
    fun getNumberOrNull(vararg nodePath: String?): Number? {
        val node = resolveNode(*nodePath)
        if (node.virtual()) {
            return null
        }
        return node.raw() as? Number
    }

    fun getConfigMaker(vararg nodePath: String?): C {
        return createConfigurator(getNode(*nodePath))
    }

    override fun getSubConfig(vararg path: String?): me.regadpole.plumbot.api.platform.PlatformConfig {
        return getConfigMaker(*path)
    }

    override fun getStringList(vararg path: String?): List<String?> {
        return resolveNode(*path).childrenList().map { it.string }
    }

    override fun getLongList(vararg path: String?): List<Long> {
        return resolveNode(*path).childrenList().map { it.long }
    }

    /**
     * 读取布尔节点；缺失返回 false。
     * **禁止**改用 `node.boolean`（及 `int` / `double`）：那是**带默认值拷贝**的读法，值类型
     * 对不上时把默认值**写回节点**——`render.engine: playwright` 读一次就变成 `false`，缺失键
     * 也被实体化成 `Boolean false`，`getString` 随即返回 `"false"` 而非 `null`，所有 `?: 默认值`
     * 回落同时失效（三端曾因此齐报「未知渲染引擎 'false'」并全量回落 AWT）。这里只按 `raw()`
     * 自行判定，与 [getNumberOrNull] 一样保持**纯读**。
     */
    override fun getBoolean(vararg path: String?): Boolean {
        val node = resolveNode(*path)
        if (node.virtual()) return false
        return when (val raw = node.raw()) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            // 字符串只认明确的 true/false（YAML 的 yes/no 会被解析成字符串，不能猜）
            is String -> raw.trim().equals("true", ignoreCase = true)
            else -> false
        }
    }

    override fun getInteger(vararg path: String?): Int {
        return resolveNode(*path).int
    }

    override fun getString(vararg path: String?): String? {
        return resolveNode(*path).string
    }

    override fun getLong(vararg path: String?): Long {
        return resolveNode(*path).long
    }

    override fun getDouble(vararg path: String?): Double {
        return resolveNode(*path).double
    }

    fun saveConfig(configFile: Path) {
        val loader = loaderBuilder()
            .path(configFile)
            .build()
        try {
            loader.save(config)
        } catch (e: IOException) {
            System.err.println("[PlumBot-Config] 无法保存配置文件 (${configFile.fileName}): ${e.message}")
        }
    }

    companion object {
        @JvmStatic
        fun <C : AbstractConfigurator<C>> createConfig(
            folder: Path,
            fileName: String,
            factory: (ConfigurationNode) -> C,
            loaderBuilder: () -> AbstractConfigurationLoader.Builder<*, *>
        ): C? {
            try {
                if (!folder.exists()) {
                    folder.createDirectory()
                }

                val configFile = folder.resolve(fileName)
                if (!configFile.exists()) {
                    PlumBot::class.java.getResourceAsStream("/$fileName")?.let { Files.copy(it, configFile) }
                }

                val loader = loaderBuilder()
                    .path(configFile)
                    .build()

                return factory(loader.load())
            } catch (e: IOException) {
                System.err.println("[PlumBot-Config] 无法创建或读取配置文件 ($fileName): ${e.message}")
                return null
            }
        }
    }
}
