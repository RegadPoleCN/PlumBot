package me.regadpole.plumbot.config

import taboolib.module.configuration.Configuration

class LangConfig(langConfig: Configuration) {
    private val langConf = LangConf(langConfig)

    class LangConf(config: Configuration) {
        val ver = config.getString("ver")
        val onEnable = config.getString("onEnable")
        val onDisable = config.getString("onDisable")
        val forwarding = Forwarding(config)
        val playerJoinMsg = config.getString("playerJoinMsg")
        val playerLeaveMsg = config.getString("playerLeaveMsg")
        val whitelist = Whitelist(config)
        val commandSendFinish = config.getString("commandSendFinish")
        val messageOnDie = config.getString("messageOnDie")
        class Forwarding(config: Configuration) {
            val toPlatform = config.getString("forwarding.toPlatform")
            val toServer = config.getString("forwarding.toServer")
        }
        class Whitelist(config: Configuration) {
            val kickServer = config.getString("whitelist.kickServer")
            val kickPlatform = config.getString("whitelist.kickPlatform")
            val user = User(config)
            val admin = Admin(config)
            class User(config: Configuration) {
                val addBind = config.getString("whitelist.user.addBind")
                val removeBind = config.getString("whitelist.user.removeBind")
                val queryBind = config.getString("whitelist.user.queryBind")
                val fullBind = config.getString("whitelist.user.fullBind")
                val existsBind = config.getString("whitelist.user.existsBind")
                val notExistsBind = config.getString("whitelist.user.nonExistsBind")
                val notBelongToYou = config.getString("whitelist.user.notBelongToYou")
            }
            class Admin(config: Configuration) {
                val addBind = config.getString("whitelist.admin.addBind")
                val removeBind = config.getString("whitelist.admin.removeBind")
                val queryBind = config.getString("whitelist.admin.queryBind")
            }
        }
    }

    fun getLangConf(): LangConf {
        return langConf
    }
}