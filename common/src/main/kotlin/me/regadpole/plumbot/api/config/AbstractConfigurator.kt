package me.regadpole.plumbot.api.config

import me.regadpole.plumbot.PlumBot
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.AbstractConfigurationLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

abstract class AbstractConfigurator<C : AbstractConfigurator<C>> protected constructor(
    protected val config: ConfigurationNode
) : Cloneable {

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

    fun getConfigMaker(vararg nodePath: String?): C {
        return createConfigurator(getNode(*nodePath))
    }

    fun getStringList(vararg nodePath: String?): List<String?> {
        return resolveNode(*nodePath).childrenList().map { it.string }
    }

    fun getLongList(vararg nodePath: String?): List<Long> {
        return resolveNode(*nodePath).childrenList().map { it.long }
    }

    fun getBoolean(vararg nodePath: String?): Boolean {
        return resolveNode(*nodePath).boolean
    }

    fun getInteger(vararg nodePath: String?): Int {
        return resolveNode(*nodePath).int
    }

    fun getString(vararg nodePath: String?): String? {
        return resolveNode(*nodePath).string
    }

    fun getLong(vararg nodePath: String?): Long {
        return resolveNode(*nodePath).long
    }

    fun saveConfig(configFile: Path) {
        val loader = loaderBuilder()
            .path(configFile)
            .build()
        try {
            loader.save(config)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    public abstract override fun clone(): C

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
                e.printStackTrace()
                return null
            }
        }
    }
}
