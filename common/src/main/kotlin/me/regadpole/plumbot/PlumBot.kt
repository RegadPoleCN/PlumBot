package me.regadpole.plumbot

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.task.TaskProvider
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.utils.TextToImg
import net.kyori.adventure.text.Component
import taboolib.module.database.Database
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty

interface PlumBot: TaskProvider {

    var datasource: YamlConfigurator
    var config: YamlConfigurator
    var dataDirectory: Path

    fun log(level: LogLevel, log: String)
    fun sendMessage(message: Component)
    fun kickPlayer(name: String)
    fun listPlayers(): List<String>
    fun listPlayerString(): String
    fun loadDependencies()

    fun enable() {
        TextToImg.ttfFile = File(config.getString("feature", "img", "file")!!.replace("%plugin_folder%", dataDirectory.pathString))
        loadBot()
        log(LogLevel.INFO, "Bot started!")
        loadDatabase()
        log(LogLevel.INFO, "Database initialized!")
        DebugProvider(this).load()
        log(LogLevel.INFO, "Debugging loaded!")
    }

    fun disable() {
        BotProvider.unloadBot()
        log(LogLevel.INFO, "Bot stopped!")
        DatabaseProvider.shutdown()
        log(LogLevel.INFO, "Database closed!")
    }

    fun loadBot() {
        TaskProviderImpl.submitAsync {
            val addr = URI.create("ws://" + config.getString("bot", "address"))
            val token = config.getString("bot", "token")
            if (token.isNullOrEmpty()) BotProvider.loadBot(this, addr)
            else BotProvider.loadBot(this, addr, token)
        }
    }

    fun loadDatabase() {
        TaskProviderImpl.submitAsync {
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
    }

    fun loadConfig() {
        config = YamlConfigurator.createConfig(dataDirectory, "config.yml")!!
        datasource = YamlConfigurator.createConfig(dataDirectory, "datasource.yml")!!
        val messagesConf = YamlConfigurator.createConfig(dataDirectory, "messages.yml")
        Messages::class.members.forEach{
            if (it is KMutableProperty<*>) {
                when(it.returnType.classifier) {
                    String::class -> it.setter.call(Messages, messagesConf!!.getString(it.name))
                    List::class -> it.setter.call(Messages, messagesConf!!.getStringList(it.name))
                }
                log(LogLevel.DEBUG, "messages: ${it.name} -> ${it.call(Messages)}")
            }
        }
    }
}