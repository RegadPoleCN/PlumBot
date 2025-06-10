package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.net.URI

object BotProvider {

    private var bot: IBot? = null

    private var hasLoaded = false

    fun loadBot(plugin: PlumBot, type: String) {
        when(type.lowercase()) {
            "mirai" -> loadMiraiMCBot(plugin)
            "onebot" -> {
                val addr = URI.create("ws://" + plugin.config.getString("bot", "onebot", "address"))
                val token = plugin.config.getString("bot", "onebot", "token")
                if (token.isNullOrEmpty()) loadOneBot(plugin, addr)
                else loadOneBot(plugin, addr, token)
            }
        }
    }

    fun unloadBot() {
        if (bot != null && hasLoaded) {
            bot!!.shutdown()
            bot = null
        }
    }

    internal fun loadOneBot(plugin: PlumBot, uri: URI) {
        try {
            val client = WebsocketBotClient(uri)
            bot = Onebot(plugin, client).start()
            hasLoaded = true
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to OneBot's websocket server!", e)
        }
    }

    internal fun loadOneBot(plugin: PlumBot, uri: URI, token: String) {
        try {
            val client = WebsocketBotClient(uri, token)
            bot = Onebot(plugin, client).start()
            hasLoaded = true
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to OneBot's websocket server!", e)
        }
    }

    internal fun loadMiraiMCBot(plugin: PlumBot) {
        bot = MiraiMCBot(plugin).start()
        hasLoaded = true
    }

    fun getBot(): IBot? {
        return bot
    }
}