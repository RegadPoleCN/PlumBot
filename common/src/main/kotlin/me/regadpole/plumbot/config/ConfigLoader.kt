package me.regadpole.plumbot.config

import taboolib.common.io.newFile
import taboolib.common.platform.function.getDataFolder
import taboolib.module.configuration.Configuration
import taboolib.module.configuration.util.getMap
import taboolib.module.database.getHost

class ConfigLoader(config: Configuration, commandsConfig: Configuration, returnsConfig: Configuration) {
    private val config = Config(config)
    private val commands = Commands(commandsConfig)
    private val returns = Returns(returnsConfig)


    class Config(config: Configuration){
        val ver = config.getString("Ver")
        val lang = config.getString("lang")
        val prefix = config.getString("commandPrefix")
        val forwarding = Forwarding(config)
        val dieReport = config.getBoolean("dieReport")
        val whiteList = WhiteList(config)
        val cmd = config.getBoolean("cmd")
        val joinAndLeave = config.getBoolean("joinAndLeave")
        val online = config.getBoolean("online")
        val tps = config.getBoolean("tps")
        val sdc = config.getBoolean("SDC")
        val sdr = config.getBoolean("SDR")
        val database = DataBase(config)
        val enableGroups = config.getStringList("enableGroups")
        val botAdmins = config.getStringList("botAdmins")
        val bot = Bot(config)
        class Bot(config: Configuration){
            val mode = config.getString("bot.mode")
            val gocqhttp = Gocqhttp(config)
            val kook = Kook(config)
            class Gocqhttp(config: Configuration){
                val ws = config.getString("bot.go-cqhttp.ws")
                val token = config.getString("bot.go-cqhttp.token")
                val hasAccessToken = config.getBoolean("bot.go-cqhttp.hasAccessToken")
            }
            class Kook(config: Configuration) {
                val token = config.getString("bot.kook.token")
            }
        }
        class Forwarding(config: Configuration) {
            val enable = config.getBoolean("forwarding.enable")
            val mode = config.getInt("forwarding.mode")
            val prefix = config.getString("forwarding.prefix")
        }
        class WhiteList(config: Configuration) {
            val enable = config.getBoolean("whiteList.enable")
            val maxCount = config.getInt("whitelist.maxCount")
        }
        class DataBase(config: Configuration) {
            val type = config.getString("database.type")
            private val sqlitePath =
                config.getString("database.setting.sqlite.path")?.replace("%plugin_folder%", getDataFolder().path)
            val sqliteHost = newFile(sqlitePath!!, create = true, folder = false).getHost()
            val host = config.getHost("database.settings.mysql")
        }
    }

    class Commands(config: Configuration) {
        val ver = config.getString("ver")
        val adminCommand = config.getMap<String, String>("admin")
        val userCommand = config.getMap<String, String>("user")
    }

    class Returns(config: Configuration) {
        val ver = config.getString("ver")
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

}