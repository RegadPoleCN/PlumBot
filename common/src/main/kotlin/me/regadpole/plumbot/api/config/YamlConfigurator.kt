package me.regadpole.plumbot.api.config

import me.regadpole.plumbot.PlumBot
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.loader.ConfigurationLoader
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists


class YamlConfigurator(private val config: ConfigurationNode): Cloneable {
    fun getNode(): ConfigurationNode {
        return config
    }

    private fun resolveNode(vararg nodePath: String?): ConfigurationNode {
        val resolvedPath = nodePath.flatMap { path -> path?.split('.') ?: listOf(null) }.toTypedArray()
        return config.node(*resolvedPath)
    }

    fun getNode(vararg nodePath: String?): ConfigurationNode {
        return resolveNode(*nodePath)
    }

    fun getConfigMaker(vararg nodePath: String?): YamlConfigurator {
        return YamlConfigurator(getNode(*nodePath))
    }

    /*method to obtain a string list from the yml file */
    fun getStringList(vararg nodePath: String?): List<String?> {
        return resolveNode(*nodePath).childrenList().map { it.string }
    }

    /*method to obtain a long list from the yml file */
    fun getLongList(vararg nodePath: String?): List<Long> {
        return resolveNode(*nodePath).childrenList().map { it.long }
    }

    /*method to get boolean value from yml file*/
    fun getBoolean(vararg nodePath: String?): Boolean {
        return resolveNode(*nodePath).boolean
    }

    /*method to get integer value from yml file*/
    fun getInteger(vararg nodePath: String?): Int {
        return resolveNode(*nodePath).int
    }

    /*method to get string from yml file*/
    fun getString(vararg nodePath: String?): String? {
        return resolveNode(*nodePath).string
    }

    /*method to get long value from yml file*/
    fun getLong(vararg nodePath: String?): Long {
        return resolveNode(*nodePath).long
    }

    /*method to copy the data of the configuration node into the actual yml file
    * here you need to define the configuration node that holds the data and the path to the yml file
    * Example: saveConfig("config", Paths.get("XYZplugin","config.yml")) */
    fun saveConfig(configFile: Path) {
        val loader: ConfigurationLoader<*> = YamlConfigurationLoader.builder()
            .path(configFile)
            .build()
        try {
            loader.save(config)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    companion object {
        /*method to create yml file along with folder inside plugins folder
   * and copy the data from the yml in resources folder to the configuration node
   * while using this method, you can put your plugin name in place of folder
   * and the name of yml file in your resource package
   * Example: config = createConfig("XYZplugin","config.yml")*/
        @JvmStatic
        fun createConfig(folder: Path, fileName: String): YamlConfigurator? {
//        val pluginFolder = Paths.get("plugins", folderName)

            try {
                if (!folder.exists()) {
                    folder.createDirectory()
                }

                val configFile = folder.resolve(fileName)
                if (!configFile.exists()) {
                    PlumBot::class.java.getResourceAsStream("/$fileName")?.let { Files.copy(it, configFile) }
                }

                val loader: ConfigurationLoader<*> = YamlConfigurationLoader.builder()
                    .path(configFile)
                    .build()

                return YamlConfigurator(loader.load())
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
    }

    public override fun clone(): YamlConfigurator {
        return super.clone() as YamlConfigurator
    }
}