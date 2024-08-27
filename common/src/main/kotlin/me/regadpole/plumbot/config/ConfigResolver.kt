package me.regadpole.plumbot.config

import me.regadpole.plumbot.PlumBot
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.Type
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*


object ConfigResolver {

    private val plugin: PlumBot = PlumBot
    private lateinit var config: ConfigLoader
    private lateinit var lang: LangConfig

    @Throws(IOException::class)
    fun loadConfig(): ConfigResolver {
        val commandsFile = File(getDataFolder(), "commands.yml")
        val configFile = File(getDataFolder(), "config.yml")
        val returnsFile = File(getDataFolder(), "returns.yml")
        val fontFile = File(getDataFolder(), "MiSans-Normal.ttf")
        val kook = File(getDataFolder(), "kook")
        val kookConf = File(kook, "kbc.yml")
        val kookPlu = File(kook, "plugins")
        val langDir = File(getDataFolder(),"lang")

        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw RuntimeException("Failed to create data folder!")
        val allFile = arrayOf(configFile, commandsFile, returnsFile, fontFile)
        for (file in allFile) {
            if (!file.exists()) {
                plugin.javaClass.getResourceAsStream("/" + file.name).use { inputStream ->
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
                ("/" + kookConf.parentFile.name) + "/" + kookConf.name
            ).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, kookConf.toPath())
            }
        }
        if (!langDir.exists()) {
            langDir.mkdirs()
        }
        langDir.listFiles()?.forEach {
            if (it.exists()) return@forEach
            plugin.javaClass.getResourceAsStream(
                ("/" + langDir.name + "/" + it.name)
            ).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, it.toPath())
            } }


        config = ConfigLoader(Configuration.loadFromFile(configFile, Type.YAML), Configuration.loadFromFile(commandsFile, Type.YAML), Configuration.loadFromFile(returnsFile, Type.YAML))

        if ("2.0.0" != config.getConfig().ver) {
            plugin.javaClass.getResourceAsStream("/" + configFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
                return this
            }
        }
        if ("2.0.0" != config.getCommands().ver) {
            plugin.javaClass.getResourceAsStream("/" + commandsFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, commandsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
                return this
            }
        }
        if ("2.0.0" != config.getReturns().ver) {
            plugin.javaClass.getResourceAsStream("/" + returnsFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, returnsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
                return this
            }
        }

        val langFile = File(langDir, config.getConfig().lang+".yml")
        lang = LangConfig(Configuration.loadFromFile(langFile, Type.YAML))
        if ("2.0.0" != plugin.getLangConfig().getLangConf().ver) {
            plugin.javaClass.getResourceAsStream("/lang/" + langFile.getName()).use { inputStream ->
                checkNotNull(inputStream)
                Files.copy(inputStream, langFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                loadConfig()
                return this
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
    fun getLangConf():LangConfig {
        return lang
    }
}