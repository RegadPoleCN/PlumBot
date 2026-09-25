package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.platform.PlatformContext

/**
 * 负责向所有配置互通的群广播消息的发送组件。
 */
class ServerMessageSender(private val context: PlatformContext) {

    fun broadcast(message: String, isPic: Boolean = false) {
        val bot = BotProvider.getBot() ?: return
        context.config.getLongList("groups").forEach { groupId ->
            bot.sendMsg(true, groupId, message, isPic)
        }
    }

    companion object {
        fun broadcastToGroups(
            config: YamlConfigurator,
            message: String,
            isPic: Boolean,
            bot: IBot? = BotProvider.getBot()
        ) {
            val targetBot = bot ?: return
            config.getLongList("groups").forEach { groupId ->
                targetBot.sendMsg(true, groupId, message, isPic)
            }
        }
    }
}
