package me.regadpole.plumbot

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.platform.PlatformTaskHandle
import me.regadpole.plumbot.task.TaskProvider
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.utils.TextToImg
import net.kyori.adventure.text.Component
import taboolib.module.database.Database
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Future
import kotlinx.coroutines.Job
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty

interface PlumBot: TaskProvider {

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
        TextToImg.ttfFile = File(config.getString("feature", "img", "file")!!.replace("%plugin_folder%", dataDirectory.pathString))
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

fun Future<*>.asPlatformTaskHandle(): PlatformTaskHandle {
    if (this is PlatformTaskHandle) return this
    return object : PlatformTaskHandle {
        override val job = Job()
        override fun cancel(): Boolean {
            val cancelled = this@asPlatformTaskHandle.cancel(true)
            job.cancel()
            return cancelled
        }
    }
}