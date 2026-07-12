package me.regadpole.plumbot.listener

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.bot.command.MessageForwardService
import me.regadpole.plumbot.bot.command.PlayerListCommandService
import me.regadpole.plumbot.bot.command.WhitelistCommandService
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.platform.PlatformContext

class DefaultBotHandler(context: PlatformContext, bot: IBot): BotHandler {
    private val commandService = BotCommandService(context, bot)
    private val whitelistCommandService = WhitelistCommandService(commandService)
    private val playerListCommandService = PlayerListCommandService(commandService)
    private val messageForwardService = MessageForwardService(commandService)

    override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
        commandService.bot.requireCapability(BotCapability.GROUP_MESSAGE_RECEIVE)

        val keys = commandService.config.getConfigMaker("keys")
        val prefix = commandService.config.getString("feature", "cmdPrefix")
        if (handleCommand(
                message,
                prefix,
                keys.getStringList("list"),
                "list",
                { cmdPrefix, key -> """$cmdPrefix$key""" },
                { cmdPrefix, key -> message.replace("$cmdPrefix$key", "") }
            ) { onPlayerList(it, groupId, userId) }
        ) return
        if (handleCommand(
                message,
                prefix,
                keys.getStringList("addBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key (.+)""" },
                { cmdPrefix, key -> message.replace("$cmdPrefix$key ", "") }
            ) { onWhitelistApply(it, groupId, userId) }
        ) return
        if (handleCommand(
                message,
                prefix,
                keys.getStringList("deleteBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key (.+)""" },
                { cmdPrefix, key -> message.replace("$cmdPrefix$key ", "") }
            ) { onWhitelistRemove(it, groupId, userId) }
        ) return
        if (handleCommand(
                message,
                prefix,
                keys.getStringList("queryBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key(.*)""" },
                { cmdPrefix, key -> message.replace("$cmdPrefix$key", "") }
            ) { onWhitelistQuery(it, groupId, userId) }
        ) return

        messageForwardService.forward(message, groupId, userId)
    }

    override fun onWhitelistApply(message: String, groupId: Long, userId: Long) {
        whitelistCommandService.apply(message, groupId, userId)
    }

    override fun onWhitelistQuery(message: String, groupId: Long, userId: Long) {
        whitelistCommandService.query(message, groupId, userId)
    }

    override fun onWhitelistRemove(message: String, groupId: Long, userId: Long) {
        whitelistCommandService.remove(message, groupId, userId)
    }

    override fun onPlayerList(message: String, groupId: Long, userId: Long) {
        playerListCommandService.list(message, groupId, userId)
    }

    override fun onUserDecrease(groupId: Long, userId: Long) {
        commandService.bot.requireCapability(BotCapability.GROUP_MEMBER_DECREASE_RECEIVE)
        whitelistCommandService.onUserDecrease(groupId, userId)
    }

    private fun handleCommand(
        message: String,
        prefix: String?,
        keys: List<String?>,
        feature: String,
        regex: (String?, String?) -> String,
        payload: (String?, String?) -> String,
        handler: (String) -> Unit
    ): Boolean {
        keys.forEach {
            if (regex(prefix, it).toRegex().matches(message) && commandService.isFeatureEnabled(feature)) {
                handler(payload(prefix, it))
                return true
            }
        }
        return false
    }
}
