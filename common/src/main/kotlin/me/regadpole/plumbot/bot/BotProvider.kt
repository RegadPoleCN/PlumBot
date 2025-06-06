package me.regadpole.plumbot.bot

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.listener.OnebotListener
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.net.URI

object BotProvider {

    private var bot: IBot? = null

    private var hasLoaded = false

    fun loadBot(plugin: PlumBot, uri: URI) {
        try {
            val client = WebsocketBotClient(uri)
            bot = Onebot(plugin, client)
            bot!!.start()
            if (!hasLoaded) {
                client.registerEvent(OnebotListener(bot as Onebot))
            }
            hasLoaded = true
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to OneBot's websocket server!", e)
        }
    }

    fun loadBot(plugin: PlumBot, uri: URI, token: String) {
        try {
            val client = WebsocketBotClient(uri, token)
            bot = Onebot(plugin, client)
            bot!!.start()
            if (!hasLoaded) {
                client.registerEvent(OnebotListener(bot as Onebot))
            }
            hasLoaded = true
        } catch (e: Exception) {
            throw RuntimeException("Failed to connect to OneBot's websocket server!", e)
        }
    }

    fun unloadBot() {
        if (bot != null && bot!!.client.isConnected) {
            bot!!.shutdown()
            bot = null
        }
    }

    fun getBot(): IBot? {
        return bot
    }
}