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

    private class CommandRegistration(
        val keys: List<String?>,
        val feature: String,
        val regex: (String, String?) -> String,
        val payload: (String, String?, String) -> String,
        val handler: (String, Long, Long) -> Unit
    )

    private val commands: List<CommandRegistration> by lazy {
        val keys = commandService.config.getConfigMaker("keys")
        listOf(
            CommandRegistration(
                keys.getStringList("list"),
                "list",
                { cmdPrefix, key -> """$cmdPrefix$key""" },
                { cmdPrefix, key, message -> message.replace("$cmdPrefix$key", "") }
            ) { message, groupId, userId -> onPlayerList(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("addBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key (.+)""" },
                { cmdPrefix, key, message -> message.replace("$cmdPrefix$key ", "") }
            ) { message, groupId, userId -> onWhitelistApply(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("deleteBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key (.+)""" },
                { cmdPrefix, key, message -> message.replace("$cmdPrefix$key ", "") }
            ) { message, groupId, userId -> onWhitelistRemove(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("queryBind"),
                "bind",
                { cmdPrefix, key -> """$cmdPrefix$key(.*)""" },
                { cmdPrefix, key, message -> message.replace("$cmdPrefix$key", "") }
            ) { message, groupId, userId -> onWhitelistQuery(message, groupId, userId) }
        )
    }

    override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
        commandService.bot.requireCapability(BotCapability.GROUP_MESSAGE_RECEIVE)

        val prefix = commandService.config.getString("feature", "cmdPrefix") ?: ""

        for (cmd in commands) {
            if (handleCommand(
                    message,
                    prefix,
                    cmd.keys,
                    cmd.feature,
                    cmd.regex,
                    cmd.payload,
                    cmd.handler,
                    groupId,
                    userId
                )
            ) return
        }

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
        prefix: String = "",
        keys: List<String?>,
        feature: String,
        regex: (String, String?) -> String,
        payload: (String, String?, String) -> String,
        handler: (String, Long, Long) -> Unit,
        groupId: Long,
        userId: Long
    ): Boolean {
        keys.forEach {
            if (regex(prefix, it).toRegex().matches(message) && commandService.isFeatureEnabled(feature)) {
                handler(payload(prefix, it, message), groupId, userId)
                return true
            }
        }
        return false
    }
}
