package me.regadpole.plumbot

import me.regadpole.plumbot.bot.Bot
import me.regadpole.plumbot.bot.KookBot
import me.regadpole.plumbot.bot.QQBot
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.disablePlugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync
import taboolib.common.platform.function.warning
import java.io.File


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
        value = "cn.evole.onebot:OneBot-Client:0.4.1",
        repository = "https://maven.nova-committee.cn/releases"
    ),
    RuntimeDependency(
        value = "com.github.SNWCreations:KookBC:0.27.4",
        repository = "https://jitpack.io"
    )
)
object PlumBot : Plugin() {

    @JvmStatic
    lateinit var INSTANCE: PlumBot

    @JvmStatic
    lateinit var bot:Bot

    override fun onLoad() {
        INSTANCE = this
    }

    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        info("Successfully running PlumBot!")
    }

    override fun onActive() {
        submitAsync(now = true) {
            when (Config.getBotMode()) {
                "go-cqhttp" -> {
                    bot = QQBot()
                    bot.start()
                    info("已启动go-cqhttp服务")
                }
                "kook" -> {
                    bot = KookBot()
                    bot.start()
                    (bot as KookBot).setKookEnabled(true)
                    info("已启动kook服务")
                }
                else -> {
                    warning("无法启动服务，请检查配置文件，插件已关闭")
                    disablePlugin()
                }
            }
        }
    }

    override fun onDisable() {
        when (Config.getBotMode()) {
            "go-cqhttp" -> {
                bot.shutdown()
                info("已关闭go-cqhttp服务")
            }
            "kook" -> {
                bot.shutdown()
                info("已关闭kook服务")
            }
            else -> {
                warning("无法正常关闭服务，将在服务器关闭后强制关闭")
                disablePlugin()
            }
        }
    }

    fun getBot(): Bot {
        return bot
    }

    fun getDataFolder(): File {
        return getDataFolder()
    }
}