package me.regadpole.plumbot.config

import taboolib.common.io.newFile
import taboolib.module.configuration.ConfigFile
import taboolib.module.database.getHost

class ConfigLoader(config: ConfigFile, commandsConfig: ConfigFile, returnsConfig: ConfigFile) {
    private val config = Config(config)
    private val commands = commandsConfig
    private val returns = returnsConfig

    class Config(config: ConfigFile){
        val ver = config.getString("Ver")
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
        class Bot(config: ConfigFile){
            val mode = config.getString("bot.mode")
            val gocqhttp = Gocqhttp(config)
            val kook = Kook(config)
            class Gocqhttp(config: ConfigFile){
                val ws = config.getString("bot.go-cqhttp.ws")
                val token = config.getString("bot.go-cqhttp.token")
                val hasAccessToken = config.getBoolean("bot.go-cqhttp.hasAccessToken")
            }
            class Kook(config: ConfigFile) {
                val token = config.getString("bot.kook.token")
            }
        }
        class Forwarding(config: ConfigFile) {
            val enable = config.getBoolean("forwarding.enable")
            val mode = config.getInt("forwarding.mode")
            val prefix = config.getString("forwarding.prefix")
        }
        class WhiteList(config: ConfigFile) {
            val enable = config.getBoolean("whiteList.enable")
            val kickMsg = config.getString("whiteList.kickMsg")
            val maxCount = config.getInt("whitelist.maxCount")
        }
        class DataBase(config: ConfigFile) {
            val type = config.getString("database.type")
            private val sqlitePath = config.getString("database.setting.sqlite.path")
            val sqliteHost = newFile(sqlitePath!!, create = false, folder = false).getHost()
            val host = config.getHost("database.settings.mysql")
        }
    }

    fun getConfig(): Config {
        return config
    }
    fun getCommands(): ConfigFile {
        return commands
    }
    fun getReturns(): ConfigFile {
        return returns
    }
}