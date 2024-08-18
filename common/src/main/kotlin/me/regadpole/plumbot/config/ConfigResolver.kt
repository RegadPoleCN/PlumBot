package me.regadpole.plumbot.config

import me.regadpole.plumbot.PlumBot
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*


object ConfigResolver {

    private val plugin: PlumBot = PlumBot.INSTANCE
    private lateinit var config: ConfigLoader

    @Throws(IOException::class)
    fun loadConfig(): ConfigResolver {
        val commandsFile = File(plugin.getDataFolder(), "commands.yml")
        val configFile = File(plugin.getDataFolder(), "config.yml")
        val returnsFile = File(plugin.getDataFolder(), "returns.yml")
        val kook = File(plugin.getDataFolder(), "kook")
        val kookConf = File(kook, "kbc.yml")
        val kookPlu = File(kook, "plugins")

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) throw RuntimeException("Failed to create data folder!")
        val allFile = arrayOf(configFile, commandsFile, returnsFile)
        for (file in allFile) {
            if (!file.exists()) {
                plugin.javaClass.getResourceAsStream("/" + file.getName()).use { inputStream ->
                    checkNotNull(inputStream)
                    Files.copy(inputStream, file.toPath())
                }
            }
        }

        if (!kook.exists()) {
            kook.mkdirs()
        }
        if (!kookPlu.exists()) {
            kookPlu.mkdirs()
        }
        if (!kookConf.exists()) {
            plugin.javaClass.getResourceAsStream(
                ("/" + kookConf.getParentFile().getName()) + "/" + kookConf.getName()
            ).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, kookConf.toPath())
            }
        }

        config = ConfigLoader(Configuration.loadFromFile(configFile, Type.YAML), Configuration.loadFromFile(commandsFile, Type.YAML), Configuration.loadFromFile(returnsFile, Type.YAML))

        if ("2.0.0" != plugin.getConfig().getConfig().ver) {
            plugin.javaClass.getResourceAsStream("/" + configFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
            }
        }
        if ("2.0.0" != plugin.getConfig().getCommands().ver) {
            plugin.javaClass.getResourceAsStream("/" + commandsFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, commandsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
            }
        }
        if ("2.0.0" != plugin.getConfig().getReturns().ver) {
            plugin.javaClass.getResourceAsStream("/" + returnsFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, returnsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
            }
        }
        return this
    }

    @Throws(IOException::class)
    fun reloadConfig(): ConfigResolver {
        return loadConfig()
    }

    fun getConfigLoader(): ConfigLoader {
        return config
    }
}