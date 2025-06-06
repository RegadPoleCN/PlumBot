package me.regadpole.plumbot.api.config

import me.regadpole.plumbot.PlumBot
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.ConfigurationLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists


class HoconConfigurator(private val config: ConfigurationNode): Cloneable {
    fun getNode(): ConfigurationNode {
        return config
    }

    fun getNode(vararg nodePath: String?): ConfigurationNode {
        return config.node(*nodePath)
    }

    fun getConfigMaker(vararg nodePath: String?): HoconConfigurator {
        return HoconConfigurator(getNode(*nodePath))
    }

    /*method to obtain a string list from the yml file */
    fun getStringListFromConfig(vararg nodePath: String?): List<String?> {
        val resultList: MutableList<String?> = ArrayList()
        val subNode = config.node(*nodePath)
        if (subNode.isList) {
            val list = subNode.childrenList()
            for (item in list) {
                resultList.add(item.string)
            }
        }
        return resultList
    }

    /*method to obtain a long list from the yml file */
    fun getLongListFromConfig(vararg nodePath: String?): List<Long> {
        val resultList: MutableList<Long> = ArrayList()
        val subNode = config.node(*nodePath)
        if (subNode.isList) {
            val list = subNode.childrenList()
            for (item in list) {
                resultList.add(item.long)
            }
        }
        return resultList
    }

    /*method to get boolean value from yml file*/
    fun getBooleanFromConfig(vararg nodePath: String?): Boolean {
        val aBoolean = config.node(*nodePath).boolean
        return aBoolean
    }

    /*method to get integer value from yml file*/
    fun getIntegerFromConfig(vararg nodePath: String?): Int {
        val integer = config.node(*nodePath).int
        return integer
    }

    /*method to get string from yml file*/
    fun getStringFromConfig(vararg nodePath: String?): String? {
        val string = config.node(*nodePath).string
        return string
    }

    /*method to get long value from yml file*/
    fun getLongFromConfig(vararg nodePath: String?): Long {
        val long = config.node(*nodePath).long
        return long
    }

    /*method to copy the data of the configuration node into actual yml file
    * here you need to define the configuration node that holds the data and the path to the yml file
    * Example: saveConfig("config", Paths.get("XYZplugin","config.yml")) */
    fun saveConfig(configFile: Path) {
        val loader: ConfigurationLoader<*> = HoconConfigurationLoader.builder()
            .path(configFile)
            .build()
        try {
            loader.save(config)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    companion object {
        /*method to create yml file along with folder inside of plugins folder
   * and copy the data from the yml in resources folder to the configuration node
   * while using this method you can put your plugin name in place of folder
   * and the name of yml file in your resource package
   * Example: config = createConfig("XYZplugin","config.yml")*/
        @JvmStatic
        fun createConfig(folder: Path, fileName: String): HoconConfigurator? {
//        val pluginFolder = Paths.get("plugins", folderName)

            try {
                if (!folder.exists()) {
                    folder.createDirectory()
                }

                val configFile = folder.resolve(fileName)
                if (!configFile.exists()) {
                    PlumBot::class.java.getResourceAsStream("/$fileName")?.let { Files.copy(it, configFile) }
                }

                val loader: ConfigurationLoader<*> = HoconConfigurationLoader.builder()
                    .path(configFile)
                    .build()

                return HoconConfigurator(loader.load())
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
    }

    public override fun clone(): HoconConfigurator {
        return super.clone() as HoconConfigurator
    }
}