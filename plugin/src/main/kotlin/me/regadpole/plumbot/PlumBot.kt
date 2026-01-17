package me.regadpole.plumbot

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.scheduler.ScheduledTask
import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.ConfigMaker
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.bot.Onebot
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import me.regadpole.plumbot.listener.ServerListener
import me.regadpole.plumbot.utils.debug
import me.regadpole.plumbot.utils.info
import me.regadpole.plumbot.utils.runTask
import me.regadpole.plumbot.utils.runTaskRepeat
import taboolib.module.database.Database
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import kotlin.reflect.KMutableProperty
import kotlin.time.Duration.Companion.minutes

class PlumBot(init: JavaPluginInit) : JavaPlugin(init) {

    companion object {
        @JvmStatic
        lateinit var INSTANCE: PlumBot
            private set
    }

    init {
        INSTANCE = this
    }

    private var autoReconnectTask: ScheduledTask? = null
    private var botTask: ScheduledTask? = null
    private var messagesConf: ConfigMaker? = null
    private var datasource: ConfigMaker? = null
    private lateinit var listener: ServerListener
    var config: ConfigMaker? = null
        private set
    var messages: Messages = Messages()
        private set
    var database: IDatabase? = null
        private set
    var bot: IBot? = null
        private set

    override fun setup() {
        loadConfig()
        info("Config loaded!")
        loadDatabase()
        info("Database initialized!")
    }

    override fun start() {
        loadBot()
        info("Bot started!")
        registerCommand()
        info("Command registered!")
        listener = ServerListener(this)
        eventRegistry.register(PlayerChatEvent::class.java, listener::onChat)
        info("Listeners registered!")
    }

    override fun shutdown() {
        bot!!.shutdown()
        info("Bot stopped!")
        database!!.close()
        info("Database closed!")
        server.scheduler.tasksByPlugin(this).forEach {
            it.cancel()
        }
    }

    fun loadConfig() {
        config = null
        messagesConf = null
        datasource = null

        config = ConfigMaker.createConfig(dataDirectory, "config.yml")
        datasource = ConfigMaker.createConfig(dataDirectory, "datasource.yml")
        messagesConf = ConfigMaker.createConfig(dataDirectory, "messages.yml")
        messages::class.members.forEach{
            if (it is KMutableProperty<*>) {
                when(it.returnType.classifier) {
                    String::class -> it.setter.call(messages, messagesConf!!.getStringFromConfig(it.name))
                    List::class -> it.setter.call(messages, messagesConf!!.getStringListFromConfig(it.name))
                }
                debug("${it.name} -> ${it.call(messages)}")
            }
        }
    }

    fun loadDatabase() {
        database = null

        Database.settingsFile = DatabaseSource(datasource!!.getNode())
        val mode = config!!.getStringFromConfig("database", "mode")
        database = when (mode) {
            "sqlite" -> SQLite()
            "mysql" -> MySQL()
            else -> error("Unknown database type.")
        }
        debug("database -> $database")

        database!!.initialize()
    }

    fun loadBot() {
        bot = null
        autoReconnectTask?.cancel()
        botTask?.cancel()
        autoReconnectTask = null
        botTask = null

        botTask = runTask {
            val addr = config!!.getStringFromConfig("bot", "address")
            val token = config!!.getStringFromConfig("bot", "token")
            val client = if (token.isNullOrEmpty()) WebsocketBotClient(addr!!.split(":")[0], addr.split(":")[1].toInt())
            else WebsocketBotClient(addr!!.split(":")[0], addr.split(":")[1].toInt(), token)
            bot = Onebot(config!!, client).start()
            autoReconnectTask = runTaskRepeat(1.minutes) {
                if (!client.isConnected) client.connect()
            }
        }
    }

    private fun registerCommand() {
        val commandManager = server.commandManager
        val commandMeta = commandManager.metaBuilder("plumbot")
            .aliases("pb")
            .plugin(this)
            .build()
        val commandToRegister = PlumBotCommand.createBrigadierCommand(server, this)
        commandManager.register(commandMeta, commandToRegister)
    }
}
