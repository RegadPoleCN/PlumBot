package me.regadpole.plumbot.command

import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.utils.WhitelistHelper

object CommandHandler {
    private val commands = arrayOf("reload", "addBind", "queryBind", "deleteBind")

    /**
     * 处理 /plumbot 命令的入口方法
     * @param args 命令参数数组
     * @param source 命令发送者
     * @param plugin PlumBot插件实例
     */
    fun handleCommand(args: Array<String>, source: PlumBotCommandSource, plugin: PlumBot) {
        // 如果没有参数，显示用法
        if (args.isEmpty()) {
            sendNoCommandFound(source, plugin)
            return
        }

        val command = args[0]
        val remainingArgs = args.drop(1).toTypedArray()

        when (command) {
            "reload" -> handleReload(source, plugin, remainingArgs)
            "addBind" -> handleAddBind(source, plugin, remainingArgs)
            "deleteBind" -> handleDeleteBind(source, plugin, remainingArgs)
            "queryBind" -> handleQueryBind(source, plugin, remainingArgs)
            else -> sendNoCommandFound(source, plugin)
        }
    }

    /**
     * 处理 reload 命令
     */
    private fun handleReload(source: PlumBotCommandSource, plugin: PlumBot, args: Array<String>) {
        if (!source.hasPermission("plumbot.admin.reload")) sendNoCommandFound(source, plugin)
        plugin.restart()
        
        val message = "${plugin.messages.prefix}Reload completed!"
        source.sendMessage(message)
    }

    /**
     * 处理 addBind 命令
     */
    private fun handleAddBind(source: PlumBotCommandSource, plugin: PlumBot, args: Array<String>) {
        if (!source.hasPermission("plumbot.admin.bind")) sendNoCommandFound(source, plugin)
        if (args.size != 2) {
            sendNoCommandFound(source, plugin)
            return
        }

        val qqId = args[0].toLongOrNull()
        val playerName = args[1]

        if (qqId == null) {
            sendError(source, plugin, "QQ ID must be a valid number")
            return
        }

        if (WhitelistHelper.getInstance(plugin).checkPlayerExists(playerName)) {
            val msg = plugin.messages.existsBind
                .replace("%player_name%", playerName)
            sendError(source, plugin, msg)
            return
        }

        plugin.database?.addBind(qqId, playerName)
        
        val message = plugin.messages.prefix +
            plugin.messages.commandAddBind
                .replace("%player%", playerName)
                .replace("%target_id%", qqId.toString())
        source.sendMessage(message)
    }

    /**
     * 处理 deleteBind 命令
     */
    private fun handleDeleteBind(source: PlumBotCommandSource, plugin: PlumBot, args: Array<String>) {
        if (!source.hasPermission("plumbot.admin.unbind")) sendNoCommandFound(source, plugin)
        if (args.size == 3 && args[0] == "qq") {
            handleDeleteBindByNum(source, plugin, args.drop(1).toTypedArray())
            return
        }
        if (args.size != 2) {
            sendNoCommandFound(source, plugin)
            return
        }

        val mode = args[0]
        val value = args[1]

        when (mode) {
            "id" -> handleDeleteBindById(source, plugin, value)
            "qq" -> handleDeleteBindByQQ(source, plugin, value)
            else -> sendNoCommandFound(source, plugin)
        }
    }

    /**
     * 通过玩家名称删除绑定
     */
    private fun handleDeleteBindById(source: PlumBotCommandSource, plugin: PlumBot, playerName: String) {
        if (!WhitelistHelper.getInstance(plugin).checkPlayerExists(playerName)) {
            val msg = plugin.messages.notExistsBind
                .replace("%player_name%", playerName)
            sendError(source, plugin, msg)
            return
        }

        plugin.database?.removeBind(playerName)
        
        // 踢出在线玩家
        val player = Universe.get().getPlayerByUsername(playerName, NameMatching.EXACT)
            val kickMessage = plugin.messages.kickServer
                .replace("%groups%", plugin.config?.getLongListFromConfig("groups").toString())
            player?.packetHandler?.disconnect(kickMessage)

        val message = plugin.messages.prefix +
            plugin.messages.commandDeleteBindById
                .replace("%player%", playerName)
        source.sendMessage(message)
    }

    /**
     * 通过QQ号删除绑定
     */
    private fun handleDeleteBindByQQ(source: PlumBotCommandSource, plugin: PlumBot, qqIdStr: String) {
        val qqId = qqIdStr.toLongOrNull()
        if (qqId == null) {
            sendError(source, plugin, "QQ ID must be a valid number")
            return
        }

        val bindings = plugin.database?.getBind(qqId)
        if (bindings.isNullOrEmpty()) {
            val msg = plugin.messages.qqEmptyBind
                .replace("%user_id%", qqIdStr)
            sendError(source, plugin, msg)
            return
        }

        plugin.database?.removeBind(qqId)
        
        // 踢出所有绑定的在线玩家
        bindings.forEach { (playerName, _) ->
            val player = Universe.get().getPlayerByUsername(playerName, NameMatching.EXACT)
                val kickMessage = plugin.messages.kickServer
                    .replace("%groups%", plugin.config?.getLongListFromConfig("groups").toString())
                player?.packetHandler?.disconnect(kickMessage)
        }

        val message = plugin.messages.prefix +
            plugin.messages.commandDeleteBindByQQ
                .replace("%target_id%", qqIdStr)
        source.sendMessage(message)
    }

    private fun handleDeleteBindByNum(source: PlumBotCommandSource, plugin: PlumBot, args: Array<String>) {
        val qqId = args[0].toLongOrNull()
        if (qqId == null) {
            sendError(source, plugin, "QQ ID must be a valid number")
            return
        }
        if (plugin.database!!.getBind(qqId).isNullOrEmpty()) {
            val msg = plugin.messages.qqEmptyBind
//                        # user_id, user_name, user_nick
                .replace("%user_id%", qqId.toString())
            sendError(source, plugin, msg)
            return
        }
        val target = plugin.database!!.removeBindByNum(qqId, args[1].toInt())
        val player = target?.let { Universe.get().getPlayerByUsername(it, NameMatching.EXACT) }
        player?.packetHandler?.disconnect(
            plugin.messages.kickServer
                .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
        )
        val message = plugin.messages.prefix +
                plugin.messages.commandDeleteBindById
                    .replace("%player%", target.orEmpty())
        source.sendMessage(message)
    }

    /**
     * 处理 queryBind 命令
     */
    private fun handleQueryBind(source: PlumBotCommandSource, plugin: PlumBot, args: Array<String>) {
        if (!source.hasPermission("plumbot.admin.query")) sendNoCommandFound(source, plugin)
        if (args.size != 2) {
            sendNoCommandFound(source, plugin)
            return
        }

        val mode = args[0]
        val value = args[1]

        when (mode) {
            "id" -> handleQueryBindById(source, plugin, value)
            "qq" -> handleQueryBindByQQ(source, plugin, value)
            else -> sendNoCommandFound(source, plugin)
        }
    }

    /**
     * 通过玩家名称查询绑定
     */
    private fun handleQueryBindById(source: PlumBotCommandSource, plugin: PlumBot, playerName: String) {
        val qqId = plugin.database?.getBind(playerName)
        if (qqId == null) {
            val msg = plugin.messages.idEmptyBind
                .replace("%player%", playerName)
            sendError(source, plugin, msg)
            return
        }

        val message = plugin.messages.prefix +
            plugin.messages.commandQueryBindById
                .replace("%player%", playerName)
                .replace("%target_id%", qqId.toString())
        source.sendMessage(message)
    }

    /**
     * 通过QQ号查询绑定
     */
    private fun handleQueryBindByQQ(source: PlumBotCommandSource, plugin: PlumBot, qqIdStr: String) {
        val qqId = qqIdStr.toLongOrNull()
        if (qqId == null) {
            sendError(source, plugin, "QQ ID must be a valid number")
            return
        }

        val bindings = plugin.database?.getBind(qqId)
        if (bindings.isNullOrEmpty()) {
            val msg = plugin.messages.qqEmptyBind
                .replace("%target_id%", qqIdStr)
            sendError(source, plugin, msg)
            return
        }

        val message = plugin.messages.prefix +
            plugin.messages.commandQueryBindByQQ
                .replace("%target_id%", qqIdStr)
                .replace("%player%", bindings.keys.joinToString())
        source.sendMessage(message)
    }

    /**
     * 发送命令未找到消息
     */
    private fun sendNoCommandFound(source: PlumBotCommandSource, plugin: PlumBot) {
        val message = plugin.messages.prefix + plugin.messages.noCommandFound
        source.sendMessage(message)
    }

    /**
     * 发送错误消息
     */
    private fun sendError(source: PlumBotCommandSource, plugin: PlumBot, errorMsg: String) {
        val message = plugin.messages.prefix + errorMsg
        source.sendMessage(message)
    }
}