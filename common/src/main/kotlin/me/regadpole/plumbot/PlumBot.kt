package me.regadpole.plumbot

import me.regadpole.plumbot.bot.Bot
import me.regadpole.plumbot.bot.KookBot
import me.regadpole.plumbot.bot.QQBot
import me.regadpole.plumbot.config.ConfigLoader
import me.regadpole.plumbot.config.ConfigResolver
import me.regadpole.plumbot.config.LangConfig
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Plugin
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.disablePlugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import taboolib.module.lang.event.PlayerSelectLocaleEvent
import taboolib.module.lang.event.SystemSelectLocaleEvent


@RuntimeDependencies(
    RuntimeDependency(
        value = "org.java-websocket:Java-WebSocket:1.5.5",
    ),
    RuntimeDependency(
        value = "net.kyori:event-method:3.0.0",
    ),
    RuntimeDependency(
        value = "net.kyori:event-api:3.0.0",
    ),
    RuntimeDependency(
        value = "com.github.RegadPoleCN:onebot-client:f73b158fc4",
        repository = "https://jitpack.io"
    ),
    RuntimeDependency(
        value = "com.github.SNWCreations:KookBC:0.27.4",
        repository = "https://jitpack.io"
    )
)
object PlumBot : Plugin() {

    private lateinit var bot: Bot

    private lateinit var config: ConfigLoader

    private lateinit var lang: LangConfig

    override fun onLoad() {
        ConfigResolver.loadConfig()
        config = ConfigResolver.getConfigLoader()
        lang =ConfigResolver.getLangConf()
    }

    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        info("Successfully running PlumBot!")
    }

    override fun onActive() {
        submitAsync(now = true) {
            when (config.getConfig().bot.mode) {
                "go-cqhttp" -> {
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
    }

    override fun onDisable() {
        when (config.getConfig().bot.mode) {
            "go-cqhttp", "kook" -> {
                bot.shutdown()
            }
            else -> {
                warning("无法正常关闭服务，将在服务器关闭后强制关闭")
                disablePlugin()
            }
        }
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

    @SubscribeEvent
    fun lang(event: PlayerSelectLocaleEvent) {
        event.locale = getConfig().getConfig().lang!!
    }

    @SubscribeEvent
    fun lang(event: SystemSelectLocaleEvent) {
        event.locale = getConfig().getConfig().lang!!
    }
}