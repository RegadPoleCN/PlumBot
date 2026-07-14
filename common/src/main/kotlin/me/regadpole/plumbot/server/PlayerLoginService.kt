package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent

class PlayerLoginService(private val config: YamlConfigurator) {
    fun check(playerName: String): PreLoginResult {
        if (config.getBoolean("feature", "bind", "whitelist")) {
            val qq = DatabaseProvider.getBindByName(playerName)
            if (qq.isNullOrEmpty()) {
                notifyKick(playerName)
                return PreLoginResult(
                    allowed = false,
                    kickMessage = getLegacyFromComponent(
                        getComponentFromMiniMsg(
                            Messages.kickServer
                                .replace("%groups%", config.getLongList("groups").toString())
                        )
                    )
                )
            }

            if (config.getBoolean("feature", "joinAndLeave", "joinProxy")) {
                notifyJoinProxy(playerName)
            }
            return PreLoginResult(allowed = true)
        }

        if (config.getBoolean("feature", "joinAndLeave", "joinProxy")) {
            notifyJoinProxy(playerName)
        }
        return PreLoginResult(allowed = true)
    }

    private fun notifyKick(playerName: String) {
        val rendered = Messages.kickPlatform
            .replace("%player_name%", playerName)
        ServerMessageSender.broadcastToGroups(
            config,
            rendered,
            config.getBoolean("feature", "bind", "pic")
        )
    }

    private fun notifyJoinProxy(playerName: String) {
        PlayerJoinLeaveService.notifyJoin(playerName, config)
    }
}