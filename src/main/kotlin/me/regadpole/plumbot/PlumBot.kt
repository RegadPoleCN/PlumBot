package me.regadpole.plumbot

import me.regadpole.plumbot.bot.Bot
import taboolib.common.env.RuntimeDependencies
import taboolib.common.env.RuntimeDependency
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

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
    lateinit var bot:Bot

    // 项目使用TabooLib Start Jar 创建!
    override fun onEnable() {
        info("Successfully running PlumBot!")
    }

    fun getBot(): Bot {
        return bot
    }
}