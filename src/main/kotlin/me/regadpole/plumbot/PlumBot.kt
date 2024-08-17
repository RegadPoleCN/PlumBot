package me.regadpole.plumbot

import me.regadpole.plumbot.bot.Bot
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

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