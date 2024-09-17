package me.regadpole.plumbot.config

import taboolib.common.platform.function.getDataFolder
import taboolib.library.configuration.ConfigurationSection
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getMap
import taboolib.module.database.getHost

class ConfigLoader(config: Configuration, commandsConfig: Configuration, returnsConfig: Configuration) {
    private val config = Config(config)
    private val commands = Commands(commandsConfig)
    private val returns = Returns(returnsConfig)
    private val tabooConfig = config


    class Config(config: Configuration){
        val version = config.getString("version")
        val lang = config.getString("lang")
        class Groups(config: Configuration) {
            val enableGroups = config.getStringList("groups.enableGroups")
            val botAdmins = config.getStringList("groups.botAdmins")
            val prefix = config.getString("groups.commandPrefix")
            val forwarding = Forwarding(config)
            class Forwarding(config: Configuration) {
                val message = Message(config)
                class Message(config: Configuration) {
                    val enable = config.getBoolean("groups.forwarding.message.enable")
                    val mode = config.getInt("groups.forwarding.message.mode")
                    val prefix = config.getString("groups.forwarding.message.prefix")
                }
                val dieMessage = config.getBoolean("groups.forwarding.dieMessage")
                val whitelist = Whitelist(config)
                class Whitelist(config: Configuration) {
                    val enable = config.getBoolean("groups.forwarding.whitelist.enable")
                    val maxCount = config.getInt("maxCount")
                }
                val cmd = config.getBoolean("groups.forwarding.cmd")
                val joinAndLeave = config.getBoolean("gorups.forwarding.joinAndLeave")
                val online = config.getBoolean("groups.forwarding.online")
                val tps = config.getBoolean("groups.forwarding.tps")
                val sdc = config.getBoolean("groups.forwarding.SDC")
                val sdr = config.getBoolean("groups.forwarding.SDR")
            }
        }
        class DataBase(config: Configuration) {
            val type = config.getString("database.type")
            val settings = Settings(config)
            class Settings(config: Configuration) {
                val sqlite = SQLite(config)
                val mysql = MySQL(config)
                val pool = Pool(config)
                class SQLite(config: Configuration) {
                    val path = config.getString("database.setting.sqlite.path")?.replace("%plugin_folder%", getDataFolder().path)
                }
                class MySQL(config: Configuration) {
                    val host = config.getHost("database.settings.mysql.host")
                    val port = config.getInt("database.settings.mysql.port")
                    val database = config.getString("database.settings.mysql.database")
                    val username = config.getString("database.settings.mysql.username")
                    val password = config.getString("database.settings.mysql.password")
                    val flagsURL = config.getString("database.settings.mysql.flagsURL")
                }
                class Pool(config: Configuration) {
                    val connectionTimeout = config.getInt("database.settings.pool.connectionTimeout")
                    val idleTimeout = config.getInt("database.settings.pool.idleTimeout")
                    val maxLifetime = config.getInt("database.settings.pool.maxLifetime")
                    val maximumPoolSize = config.getInt("database.settings.pool.maximumPoolSize")
                    val keepaliveTime = config.getInt("database.settings.pool.keepaliveTime")
                    val minimumIdle = config.getInt("database.settings.pool.minimumIdle")
                }
            }
            val enable = config.getBoolean("enable")
            val host = config.getString("host")
            val port = config.getInt("port")
            val database = config.getString("database")
            val user = config.getString("user")
            val password = config.getString("password")
            val table = config.getString("table")
            val columns = config.getMap<String, String>("columns")
            val prefix = config.getString("prefix")
            val suffix = config.getString("suffix")
        }
        class Bot(config: Configuration){
            val mode = config.getString("bot.mode")
            val onebot = Onebot(config)
            val kook = Kook(config)
            class Onebot(config: Configuration){
                val ws = config.getString("bot.onebot.ws")
                val token = Token(config)
                class Token(config: Configuration) {
                    val enable = config.getBoolean("bot.onebot.token.enable")
                    val token = config.getString("bot.onebot.token.token")
                }
            }
            class Kook(config: Configuration) {
                val token = config.getString("bot.kook.token")
            }
        }
        val groups = Groups(config)
        val database = DataBase(config)
        val bot = Bot(config)
    }

    class Commands(config: Configuration) {
        val version = config.getString("version")
        val adminCommand = config.getMap<String, String>("admin")
        val userCommand = config.getMap<String, String>("user")
    }

    class Returns(config: Configuration) {
        val version = config.getString("version")
        val adminReturn = config.getMap<String, String>("admin")
        val userReturn = config.getMap<String, String>("user")
    }



    fun getConfig(): Config {
        return config
    }
    fun getCommands(): Commands {
        return commands
    }
    fun getReturns(): Returns {
        return returns
    }
    fun getMySQLSection(): ConfigurationSection {
        return tabooConfig.getConfigurationSection("database.settings.mysql")!!
    }

}