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
import me.regadpole.plumbot.platform.PlatformLogger
import me.regadpole.plumbot.platform.PlatformMessenger
import me.regadpole.plumbot.platform.PlatformPlayerService
import me.regadpole.plumbot.platform.PlatformScheduler
import me.regadpole.plumbot.platform.PlatformTaskHandle
import me.regadpole.plumbot.platform.PlatformType
import me.regadpole.plumbot.task.TaskProvider
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.utils.TextToImg
import net.kyori.adventure.text.Component
import taboolib.module.database.Database
import java.io.File
import java.nio.file.Path
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
        get() = object : PlatformContext {
            override val config: YamlConfigurator
                get() = this@PlumBot.config
            override val datasource: YamlConfigurator
                get() = this@PlumBot.datasource
            override val dataDirectory: Path
                get() = this@PlumBot.dataDirectory
            override val logger: PlatformLogger = PlatformLogger { level, message ->
                this@PlumBot.log(level, message)
            }
            override val scheduler: PlatformScheduler = object : PlatformScheduler {
                override fun run(task: Runnable): PlatformTaskHandle = this@PlumBot.submit(task).asPlatformTaskHandle()

                override fun runAsync(task: Runnable): PlatformTaskHandle = this@PlumBot.submitAsync(task).asPlatformTaskHandle()

                override fun runLater(delay: Long, task: Runnable): PlatformTaskHandle = this@PlumBot.submitLater(delay, task).asPlatformTaskHandle()

                override fun runLaterAsync(delay: Long, task: Runnable): PlatformTaskHandle = this@PlumBot.submitLaterAsync(delay, task).asPlatformTaskHandle()

                override fun runTimer(delay: Long, period: Long, task: Runnable): PlatformTaskHandle = this@PlumBot.submitTimer(delay, period, task).asPlatformTaskHandle()

                override fun runTimerAsync(delay: Long, period: Long, task: Runnable): PlatformTaskHandle = this@PlumBot.submitTimerAsync(delay, period, task).asPlatformTaskHandle()
            }
            override val playerService: PlatformPlayerService = object : PlatformPlayerService {
                override fun kickPlayer(name: String) = this@PlumBot.kickPlayer(name)

                override fun listPlayers(): List<String> = this@PlumBot.listPlayers()

                override fun listPlayerString(): String = this@PlumBot.listPlayerString()
            }
            override val messenger: PlatformMessenger = PlatformMessenger { message ->
                this@PlumBot.sendMessage(message)
            }
            override val platformType: PlatformType = PlatformType.BUKKIT

            override fun isPluginAvailable(name: String): Boolean = false
        }

    private fun java.util.concurrent.Future<*>.asPlatformTaskHandle(): PlatformTaskHandle {
        return object : PlatformTaskHandle {
            override fun cancel(): Boolean = this@asPlatformTaskHandle.cancel(false)
        }
    }

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
        Messages::class.members.forEach{
            if (it is KMutableProperty<*>) {
                when(it.returnType.classifier) {
                    String::class -> it.setter.call(Messages, messagesConf!!.getString(it.name))
                    List::class -> it.setter.call(Messages, messagesConf!!.getStringList(it.name))
                }
//                log(LogLevel.DEBUG, "messages: ${it.name} -> ${it.call(Messages)}")
            }
        }
    }
}