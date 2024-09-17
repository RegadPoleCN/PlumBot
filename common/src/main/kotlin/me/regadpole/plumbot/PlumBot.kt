package me.regadpole.plumbot

import me.regadpole.plumbot.bot.Bot
import me.regadpole.plumbot.bot.KookBot
import me.regadpole.plumbot.bot.QQBot
import me.regadpole.plumbot.config.ConfigLoader
import me.regadpole.plumbot.config.ConfigResolver
import me.regadpole.plumbot.config.LangConfig
import me.regadpole.plumbot.database.Database
import me.regadpole.plumbot.database.MySQL
import me.regadpole.plumbot.database.SQLite
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.disablePlugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning


object PlumBot : Plugin() {

    private lateinit var bot: Bot

    private lateinit var config: ConfigLoader

    private lateinit var lang: LangConfig

    private lateinit var database: Database

    override fun onLoad() {
        ConfigResolver.loadConfig()
        config = ConfigResolver.getConfigLoader()
        lang =ConfigResolver.getLangConf()
        info("成功加载配置文件!")
    }

    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        database = when(config.getConfig().database.type?.lowercase()) {
            "sqlite" -> SQLite()
            "mysql" -> MySQL()
            else -> error("Unknown database type.")
        }
        database.initialize()
        info("成功连接到数据库!")
    }

    override fun onActive() {
        submitAsync(now = true) {
            when (config.getConfig().bot.mode) {
                "onebot" -> {
                    bot = QQBot(this@PlumBot)
                    bot.start()
                }
                "kook" -> {
                    bot = KookBot(this@PlumBot)
                    bot.start()
                    (bot as KookBot).setKookEnabled(true)
                }
                else -> {
                    warning("无法启动服务，请检查配置文件，插件已关闭")
                    disablePlugin()
                }
            }
        }
        info("成功启用机器人服务!")
    }

    override fun onDisable() {
        bot.shutdown()
        info("成功关闭机器人服务")
        database.close()
        info("成功关闭数据库链接!")
    }

    fun reloadConfig() {
        ConfigResolver.reloadConfig()
        config = ConfigResolver.getConfigLoader()
        lang = ConfigResolver.getLangConf()
    }

    fun getBot(): Bot {
        return bot
    }

    fun getConfig(): ConfigLoader {
        return config
    }
    fun getLangConfig(): LangConfig {
        return lang
    }

    fun getDatabase(): Database {
        return database
    }

}