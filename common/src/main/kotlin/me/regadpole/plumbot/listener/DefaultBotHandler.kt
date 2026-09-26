package me.regadpole.plumbot.listener

import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.bot.command.MessageForwardService
import me.regadpole.plumbot.bot.command.PlayerListCommandService
import me.regadpole.plumbot.bot.command.WhitelistCommandService
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.api.platform.PlatformContext

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
        val keys = commandService.config.getSubConfig("keys")
        listOf(
            CommandRegistration(
                keys.getStringList("list"),
                "list",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}$""" },
                { cmdPrefix, key, message -> "" }
            ) { message, groupId, userId -> onPlayerList(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("addBind"),
                "bind",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s+(.+)$""" },
                { cmdPrefix, key, message -> message.replaceFirst(Regex("""^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s+"""), "") }
            ) { message, groupId, userId -> onWhitelistApply(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("deleteBind"),
                "bind",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s+(.+)$""" },
                { cmdPrefix, key, message -> message.replaceFirst(Regex("""^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s+"""), "") }
            ) { message, groupId, userId -> onWhitelistRemove(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("queryBind"),
                "bind",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}(?:\s+(.*))?$""" },
                { cmdPrefix, key, message -> message.replaceFirst(Regex("""^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s*"""), "") }
            ) { message, groupId, userId -> onWhitelistQuery(message, groupId, userId) }
        )
    }

    override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
        commandService.bot.requireCapability(BotCapability.GROUP_MESSAGE_RECEIVE)

        val prefix = commandService.config.getString("feature", "cmdPrefix") ?: "/"
        val trimmed = message.trim()

        // 1. 前缀快速短路：若不是以命令前缀开头，直通消息转发，跳过无谓的正则遍历匹配
        if (!trimmed.startsWith(prefix)) {
            messageForwardService.forward(message, groupId, userId)
            return
        }

        // 2. 补齐帮助指令响应 (/help 或 /帮助)
        val commandBody = trimmed.removePrefix(prefix).trim()
        if (commandBody.equals("help", ignoreCase = true) || commandBody == "帮助") {
            sendHelpMessage(groupId, prefix)
            return
        }

        for (cmd in commands) {
            if (handleCommand(
                    trimmed,
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

    private fun sendHelpMessage(groupId: Long, prefix: String) {
        val helpLines = Messages.help.ifEmpty {
            listOf(
                "PlumBot 帮助",
                "%cmdprefix%\$list   - 查看在线人数",
                "%cmdprefix%\$addBind <游戏名>   - 申请白名单",
                "%cmdprefix%\$deleteBind <游戏名>   - 移除白名单",
                "%cmdprefix%\$queryBind   - 查询自己申请的所有白名单"
            )
        }
        val keys = commandService.config.getSubConfig("keys")
        val listKey = keys.getStringList("list").firstOrNull() ?: "在线人数"
        val addBindKey = keys.getStringList("addBind").firstOrNull() ?: "申请白名单"
        val deleteBindKey = keys.getStringList("deleteBind").firstOrNull() ?: "删除白名单"
        val queryBindKey = keys.getStringList("queryBind").firstOrNull() ?: "查询白名单"

        val formatted = helpLines.joinToString("\n")
            .replace("%cmdprefix%", prefix)
            .replace("\$list", listKey)
            .replace("\$addBind", addBindKey)
            .replace("\$deleteBind", deleteBindKey)
            .replace("\$queryBind", queryBindKey)

        commandService.sendBindMessage(groupId, formatted)
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
