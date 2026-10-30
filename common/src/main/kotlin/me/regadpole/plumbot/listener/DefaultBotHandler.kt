// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.listener

import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.bot.command.MessageForwardService
import me.regadpole.plumbot.bot.command.PlayerListCommandService
import me.regadpole.plumbot.bot.command.RemoteConsoleCommandService
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.bot.command.StatusCommandService
import me.regadpole.plumbot.bot.command.WhitelistCommandService
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.render.RenderRoute

class DefaultBotHandler(private val context: PlatformContext, bot: IBot): BotHandler {
    private val commandService = BotCommandService(context, bot)
    private val whitelistCommandService = WhitelistCommandService(commandService)
    private val playerListCommandService = PlayerListCommandService(commandService)
    private val messageForwardService = MessageForwardService(commandService)
    private val statusCommandService = StatusCommandService(commandService)
    private val remoteConsoleCommandService = RemoteConsoleCommandService(commandService)

    private class CommandRegistration(
        val keys: List<String?>,
        val feature: String,
        val regex: (String, String?) -> String,
        val payload: (String, String?, String) -> String,
        val handler: (String, Long, Long) -> Unit
    )

    /**
     * 命令注册表。**不能缓存成构造期常量**：reload 后 `config` 会换成新实例，别名只在构造期
     * 读一次的话，用户改了别名必须重启才生效且无任何提示。按"别名列表"做**廉价指纹**：
     * 指纹未变直接复用已解析的正则与 handler，变了才重建。
     */
    @Volatile
    private var commandsCache: Pair<List<List<String?>>, List<CommandRegistration>>? = null

    private val commands: List<CommandRegistration>
        get() {
            val fingerprint = commandKeysFingerprint()
            commandsCache?.let { (cachedKeys, cachedCommands) ->
                if (cachedKeys == fingerprint) return cachedCommands
            }
            return buildCommands().also { commandsCache = fingerprint to it }
        }

    private fun commandKeysFingerprint(): List<List<String?>> {
        val keys = commandService.config.getSubConfig("command", "keys")
        return listOf("list", "addBind", "deleteBind", "queryBind", "status", "remote_console", "avatar")
            .map { keys.getStringList(it) }
    }

    private fun buildCommands(): List<CommandRegistration> {
        val keys = commandService.config.getSubConfig("command", "keys")
        return listOf(
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
            ) { message, groupId, userId -> onWhitelistQuery(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("status"),
                "status",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}$""" },
                { cmdPrefix, key, message -> "" }
            ) { message, groupId, userId -> statusCommandService.showStatus(message, groupId, userId) },
            CommandRegistration(
                keys.getStringList("remote_console"),
                "remote_console",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}(?:\s+(.*))?$""" },
                { cmdPrefix, key, message -> message.replaceFirst(Regex("""^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s*"""), "") }
            ) { message, groupId, userId -> remoteConsoleCommandService.execute(message, groupId, userId) },
            // 平台原生头像卡片：Hytale 上解析原生外观图层，其余平台回落绑定 QQ 头像
            CommandRegistration(
                keys.getStringList("avatar"),
                "avatar",
                { cmdPrefix, key -> """^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}(?:\s+(.*))?$""" },
                { cmdPrefix, key, message -> message.replaceFirst(Regex("""^${Regex.escape(cmdPrefix)}${Regex.escape(key ?: "")}\s*"""), "") }
            ) { message, groupId, userId -> onAvatar(message, groupId, userId) }
        )
    }

    private fun onAvatar(message: String, groupId: Long, userId: Long) {
        val playerName = message.trim().substringBefore(' ').trim()
        if (playerName.isEmpty()) {
            commandService.sendRawMessage(groupId, commandService.render(Messages.avatarUsage))
            return
        }
        commandService.sendAvatarCard(
            groupId = groupId,
            playerName = playerName,
            fallbackText = commandService.render(Messages.avatarCard, "%player_name%" to playerName)
        )
    }

    override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
        commandService.bot.requireCapability(BotCapability.GROUP_MESSAGE_RECEIVE)

        val prefix = commandService.config.getString("command", "prefix") ?: "/"
        val trimmed = message.trim()

        if (!trimmed.startsWith(prefix)) {
            messageForwardService.forward(message, groupId, userId)
            return
        }

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
                $$"%cmdprefix%$list   - 查看在线人数",
                $$"%cmdprefix%$addBind <游戏名>   - 申请白名单",
                $$"%cmdprefix%$deleteBind <游戏名>   - 移除白名单",
                $$"%cmdprefix%$queryBind   - 查询自己申请的所有白名单",
                $$"%cmdprefix%$status   - 查看服务器性能与状态",
                $$"%cmdprefix%$avatar <游戏名>   - 查看玩家头像",
                $$"%cmdprefix%$remote_console <指令>   - [管理员] 远程执行控制台指令"
            )
        }
        val keys = commandService.config.getSubConfig("command", "keys")
        val listKey = keys.getStringList("list").firstOrNull() ?: "在线人数"
        val addBindKey = keys.getStringList("addBind").firstOrNull() ?: "申请白名单"
        val deleteBindKey = keys.getStringList("deleteBind").firstOrNull() ?: "删除白名单"
        val queryBindKey = keys.getStringList("queryBind").firstOrNull() ?: "查询白名单"
        val statusKey = keys.getStringList("status").firstOrNull() ?: "status"
        val avatarKey = keys.getStringList("avatar").firstOrNull() ?: "avatar"
        val remoteConsoleKey = keys.getStringList("remote_console").firstOrNull() ?: "cmd"

        // 逐行解析 token（而非先 join 再整体替换），以便把已解析的行数组交给 help 路由的结构化载荷
        val resolvedLines = helpLines.map { line ->
            line.replace("%cmdprefix%", prefix)
                .replace($$"$list", listKey)
                .replace($$"$addBind", addBindKey)
                .replace($$"$deleteBind", deleteBindKey)
                .replace($$"$queryBind", queryBindKey)
                .replace($$"$status", statusKey)
                .replace($$"$avatar", avatarKey)
                .replace($$"$remote_console", remoteConsoleKey)
        }
        val formatted = resolvedLines.joinToString("\n")

        commandService.sendCard(
            groupId = groupId,
            route = RenderRoute.HELP,
            card = RenderCards.help(formatted, prefix, resolvedLines)
        )
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

    /** 释放内置服务持有的协程作用域：此前从不释放，Bot 反复启停（reload / 换适配器）会泄漏 IO 线程。 */
    override fun shutdown() {
        runCatching { commandService.shutdown() }
            .onFailure { context.log(LogLevel.WARN, "[PlumBot] 命令服务释放失败：${it.message}") }
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
            if (!regex(prefix, it).toRegex().matches(message)) return@forEach

            // 命令已命中即算"已被消费"：返回 false 会让 onGroupMessage 把它当普通聊天转发，
            // 于是 `/申请白名单 xxx`、`/cmd op xxx` 这类文本会以聊天形式进入服务器。
            if (!commandService.isFeatureEnabled(feature)) {
                commandService.context.log(
                    LogLevel.DEBUG,
                    "[PlumBot] 命令 '$it' 命中但功能 '$feature' 已关闭，已忽略（不转发）。"
                )
                return true
            }

            handler(payload(prefix, it, message), groupId, userId)
            return true
        }
        return false
    }
}
