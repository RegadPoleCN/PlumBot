package me.regadpole.plumbot

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.utils.TextToImg
import net.kyori.adventure.text.Component
import taboolib.module.database.Database
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty

@StableApi
interface PlumBot {

    var datasource: YamlConfigurator
    var config: YamlConfigurator
    var dataDirectory: Path
    var debugProvider: DebugProvider

    fun log(level: LogLevel, log: String)
    fun sendMessage(message: Component)
    fun kickPlayer(name: String)
    fun listPlayers(): List<String>
    fun listPlayerString(): String
    fun loadDependencies()

    val platform: PlatformContext

    fun enable() {
        loadConfig()
        log(LogLevel.INFO, "Config loaded!")
        debugProvider.load()
        log(LogLevel.INFO, "Debugging loaded!")
        val fontPath = config.getString("feature", "img", "file")?.replace("%plugin_folder%", dataDirectory.pathString)
        if (fontPath != null) {
            TextToImg.ttfFile = File(fontPath)
        }
        loadDatabase()
        log(LogLevel.INFO, "Database initialized!")
        loadBot()
        log(LogLevel.INFO, "Bot started!")
    }

    fun disable() {
        BotProvider.unloadBot()
        log(LogLevel.INFO, "Bot stopped!")
        DatabaseProvider.shutdown()
        log(LogLevel.INFO, "Database closed!")
        TaskProviderImpl.shutdown()
        log(LogLevel.INFO, "TaskProvider shutdown!")
        debugProvider.unload()
        log(LogLevel.INFO, "Debugging unloaded!")
    }

    fun loadBot() {
        TaskProviderImpl.submitAsync {
            BotProvider.loadBot(platform, config.getString("bot", "type"))
        }
    }

    fun loadDatabase() {
        Database.settingsFile = DatabaseSource(datasource.getNode())
        val mode = config.getString("database", "mode")
        val database = when (mode) {
            "sqlite" -> SQLite(config.getString("database", "sqlite", "path")!!.replace("%plugin_folder%", dataDirectory.pathString))
            "mysql" -> MySQL(config.getNode("database", "mysql"))
            else -> {
                log(LogLevel.ERROR, "Unknown database type! Using SQLite...")
                SQLite(config.getString("database", "sqlite", "path")!!.replace("%plugin_folder%", dataDirectory.pathString))
            }
        }
        DatabaseProvider.start(database)
    }

    fun loadConfig() {
        val configFile = dataDirectory.resolve("config.yml").toFile()
        if (configFile.exists()) {
            val updated = me.regadpole.plumbot.internal.config.ConfigMigrator.completeMissingDefaults(configFile, "/config.yml")
            if (updated) {
                log(LogLevel.INFO, "检测到 config.yml 存在新增功能配置，已自动补齐默认项！")
            }
        }
        val messagesFile = dataDirectory.resolve("messages.yml").toFile()
        if (messagesFile.exists()) {
            val updated = me.regadpole.plumbot.internal.config.ConfigMigrator.completeMissingDefaults(messagesFile, "/messages.yml")
            if (updated) {
                log(LogLevel.INFO, "检测到 messages.yml 存在新增消息模板，已自动补齐默认项！")
            }
        }

        config = YamlConfigurator.createConfig(dataDirectory, "config.yml")!!
        datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")!!
        val messagesConf = YamlConfigurator.createConfig(dataDirectory, "messages.yml")
        if (messagesConf == null) {
            log(LogLevel.WARN, "messages.yml 加载失败，使用默认消息配置")
            return
        }
        Messages::class.members.forEach {
            if (it is KMutableProperty<*>) {
                when (it.returnType.classifier) {
                    String::class -> {
                        val value = messagesConf.getString(it.name)
                        if (value != null) it.setter.call(Messages, value)
                    }
                    List::class -> it.setter.call(Messages, messagesConf.getStringList(it.name))
                }
//                log(LogLevel.DEBUG, "messages: ${it.name} -> ${it.call(Messages)}")
            }
        }
    }
}