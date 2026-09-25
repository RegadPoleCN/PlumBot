package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.getComponentFromMiniMsg
import me.regadpole.plumbot.utils.getLegacyFromComponent

class PlayerLoginService(private val context: PlatformContext) {

    private val config get() = context.config
    private val sender = ServerMessageSender(context)

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
            return PreLoginResult(allowed = true)
        }
        return PreLoginResult(allowed = true)
    }

    private fun notifyKick(playerName: String) {
        val rendered = Messages.kickPlatform.replace("%player_name%", playerName)
        sender.broadcast(rendered, config.getBoolean("feature", "bind", "pic"))
    }
}