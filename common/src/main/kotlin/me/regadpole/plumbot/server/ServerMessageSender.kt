package me.regadpole.plumbot.server

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.YamlConfigurator
import me.regadpole.plumbot.bot.BotProvider

/**
 * Helper for broadcasting a single message to every group configured under
 * the `groups` list of [YamlConfigurator].
 *
 * Existing callers apply their own placeholder substitutions before invoking
 * this helper, so the message is treated as already-resolved. The helper
 * preserves the original `forEach` traversal order of the `groups` list and
 * the `isPic` flag semantics of [IBot.sendMsg].
 */
object ServerMessageSender {
    fun broadcastToGroups(
        config: YamlConfigurator,
        message: String,
        isPic: Boolean,
        bot: IBot? = BotProvider.getBot()
    ) {
        config.getLongList("groups").forEach { groupId ->
            bot?.sendMsg(true, groupId, message, isPic)
        }
    }
}