package me.regadpole.plumbot.listener

import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.internal.dispatcher.CommandDispatcher
import me.regadpole.plumbot.utils.Formatter
import me.regadpole.plumbot.utils.WhitelistHelper
import me.regadpole.plumbot.utils.getMessageFromString
import me.regadpole.plumbot.utils.runTaskAsync
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class BotHandler(private val plugin: PlumBot, private val bot: IBot) {
    fun onGroupMessage(event: GroupMessageEvent) {
//        val lock = Object()
        val message = parseGroupMessage(event)

//        (bot as Onebot).client.action(GetGroupMemberList(event.groupId)) { memberList ->
//            synchronized(lock) {
//                event.jsonMessage.forEach {
//                    val jsonObject = it.asJsonObject ?: return@forEach
//                    when (jsonObject.get("type").asString) {
//                        "text" -> message += jsonObject.get("data").asJsonObject.get("text").asString
//                        "image" -> message += "[图片]"
//                        "at" -> memberList.forEach { member ->
//                            if (member.member.userId == jsonObject.get("data").asJsonObject.get("qq").asLong) {
//                                message += "@${member.card}"
//                            }
//                        }
//                    }
//                }
//                lock.notifyAll()
//            }
//        }

//        synchronized(lock) {
//            lock.wait()
        if (handleBotCommands(message, event.groupId, event.senderId)) return

        forwardToGameServer(message, event)
//        }
    }

    private fun parseGroupMessage(event: GroupMessageEvent): String {
        var message = ""
        event.jsonMessage.forEach {
            val jsonObject = it.asJsonObject ?: return@forEach
            when (jsonObject.get("type").asString) {
                "text" -> message += jsonObject.get("data").asJsonObject.get("text").asString
                "image" -> message += "[图片]"
                "at" -> {
                    val qq = jsonObject.get("data").asJsonObject.get("qq")
                    message += if (qq.asString.equals("all", true)) {
                        "@全体成员"
                    } else {
                        bot.getGroupUserCard(event.groupId, qq.asLong)
                    }
                }
            }
        }
        return message
    }

    private fun handleBotCommands(message: String, groupId: Long, userId: Long): Boolean {
        val keys = plugin.config!!.getConfigMaker("keys")
        val prefix = plugin.config!!.getStringFromConfig("feature", "cmdPrefix")

        val commandMatchers = listOf(
            "list" to { msg: String -> onPlayerList(msg, groupId, userId) },
            "addBind" to { msg: String -> onWhitelistApply(msg, groupId, userId) },
            "deleteBind" to { msg: String -> onWhitelistRemove(msg, groupId, userId) },
            "queryBind" to { msg: String -> onWhitelistQuery(msg, groupId, userId) },
            "cmd" to { msg: String -> onRemoteCommand(msg, groupId, userId) }
        )

        for ((key, action) in commandMatchers) {
            val featureKey = if (key == "addBind" || key == "deleteBind" || key == "queryBind") "bind" else key
            if (!plugin.config!!.getBooleanFromConfig("feature", featureKey, "enable")) continue

            keys.getStringListFromConfig(key).forEach {
                val regex = when (key) {
                    "list" -> """$prefix$it"""
                    "queryBind" -> """$prefix$it(.*)"""
                    else -> """$prefix$it (.+)"""
                }.toRegex()

                if (regex.matches(message)) {
                    val args = message.replace(Regex("""$prefix$it\s*"""), "")
                    action(args)
                    return true
                }
            }
        }
        return false
    }

    private fun forwardToGameServer(message: String, event: GroupMessageEvent) {
        if (!plugin.config!!.getBooleanFromConfig("feature", "message", "enable")) return

        val mode = plugin.config!!.getIntegerFromConfig("feature", "message", "mode")
        val prefix = plugin.config!!.getStringFromConfig("feature", "message", "prefix") ?: ""
        
        val shouldForward = when (mode) {
            0 -> true
            1 -> Regex(prefix).matchesAt(message, 0)
            else -> false
        }

        if (shouldForward) {
            val content = if (mode == 1) message.replace(prefix, "") else message
            val filteredMsg = Formatter.regexFilter(content)
            if (filteredMsg == "!CANCEL") return

            val finalMsg = replacePlaceholders(
                plugin.messages.ob2server,
                groupId = event.groupId,
                userId = event.senderId,
                message = filteredMsg
            )
            Universe.get().sendMessage(getMessageFromString(finalMsg))
        }
    }

    private fun replacePlaceholders(
        template: String,
        groupId: Long? = null,
        userId: Long? = null,
        originId: Long? = null,
        message: String? = null,
        player: String? = null,
        num: Int? = null,
        current: String? = null,
        whitelistLimit: Int? = null,
        playerNum: Int? = null,
        playerList: String? = null
    ): String {
        var result = template
        groupId?.let {
            result = result.replace("%group_id%", it.toString())
            result = result.replace("%group_name%", bot.getGroupName(it))
        }
        userId?.let {
            result = result.replace("%user_id%", it.toString())
            result = result.replace("%user_name%", bot.getGroupUserName(groupId ?: 0, it))
            result = result.replace("%user_nick%", bot.getGroupUserCard(groupId ?: 0, it))
        }
        originId?.let {
            result = result.replace("%origin_id%", it.toString())
            result = result.replace("%origin_name%", bot.getGroupUserCard(groupId ?: 0, it))
        }
        message?.let { result = result.replace("%message%", it) }
        player?.let { result = result.replace("%player%", it).replace("%player_name%", it) }
        num?.let { result = result.replace("%num%", it.toString()) }
        current?.let { result = result.replace("%current%", it) }
        whitelistLimit?.let { result = result.replace("%whitelist_limit%", it.toString()) }
        playerNum?.let { result = result.replace("%player_num%", it.toString()) }
        playerList?.let { result = result.replace("%player_list%", it) }

        return result
    }

    private fun isAdmin(userId: Long): Boolean = plugin.config!!.getLongListFromConfig("admins").contains(userId)

    private fun sendBotMsg(groupId: Long, msg: String, feature: String = "bind") {
        bot.sendMsg(true, groupId, msg, plugin.config!!.getBooleanFromConfig("feature", feature, "pic"))
    }

    private fun kickPlayer(playerName: String) {
        val player = Universe.get().getPlayerByUsername(playerName, NameMatching.EXACT)
        player?.packetHandler?.disconnect(
            plugin.messages.kickServer.replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
        )
    }

    private fun onWhitelistApply(message: String, groupId: Long, userId: Long) {
        try {
            if (isAdmin(userId)) {
                val args = message.split(" ")
                val (targetId, playerName) = if (args.size == 1) userId to message else args[0].toLong() to args[1]
                
                if (WhitelistHelper.getInstance(plugin).checkPlayerExists(playerName)) {
                    val msg = replacePlaceholders(plugin.messages.existsBind, player = playerName)
                    sendBotMsg(groupId, msg)
                    return
                }

                plugin.database!!.addBind(targetId, playerName)
                val wl = plugin.database!!.getBind(targetId)!!
                val template = if (args.size == 1) plugin.messages.playerAddBind else plugin.messages.adminAddBind
                val msg = replacePlaceholders(
                    template,
                    groupId = groupId,
                    userId = if (args.size == 2) targetId else userId,
                    originId = if (args.size == 2) userId else null,
                    player = playerName,
                    num = wl.size,
                    current = wl.keys.toString()
                )
                
                sendBotMsg(groupId, msg)
            } else {
                if (WhitelistHelper.getInstance(plugin).checkUserBindingFull(userId)) {
                    val wl = plugin.database!!.getBind(userId)!!
                    val msg = replacePlaceholders(
                        plugin.messages.fullBind,
                        groupId = groupId,
                        userId = userId,
                        player = wl.keys.toString(),
                        num = wl.size,
                        whitelistLimit = plugin.config!!.getIntegerFromConfig("feature", "bind", "maxNum")
                    )
                    sendBotMsg(groupId, msg)
                    return
                }
                
                if (WhitelistHelper.getInstance(plugin).checkPlayerExists(message)) {
                    val msg = replacePlaceholders(plugin.messages.existsBind, player = message)
                    sendBotMsg(groupId, msg)
                    return
                }

                plugin.database!!.addBind(userId, message)
                val wl = plugin.database!!.getBind(userId)!!
                val msg = replacePlaceholders(
                    plugin.messages.playerAddBind,
                    groupId = groupId,
                    userId = userId,
                    player = message,
                    num = wl.size,
                    current = wl.keys.toString()
                )
                sendBotMsg(groupId, msg)
            }
        } catch (exception: Exception) {
            sendBotMsg(groupId, plugin.messages.wrongUsage)
        }
    }

    private fun onWhitelistQuery(message: String, groupId: Long, userId: Long) {
        try {
            if (message.startsWith(" ") && isAdmin(userId)) {
                val subMsg = message.trim()
                when {
                    subMsg.startsWith("id:") -> {
                        val playerName = subMsg.substring(3).trim()
                        val boundUserId = plugin.database!!.getBind(playerName)
                        if (boundUserId == null) {
                            sendBotMsg(groupId, replacePlaceholders(plugin.messages.idEmptyBind, player = playerName))
                            return
                        }
                        val msg = replacePlaceholders(
                            plugin.messages.adminQueryIdBind,
                            groupId = groupId,
                            originId = userId,
                            userId = boundUserId,
                            player = playerName
                        )
                        sendBotMsg(groupId, msg)
                        return
                    }
                    subMsg.startsWith("qq:") -> {
                        val targetId = subMsg.substring(3).trim().toLong()
                        val wl = plugin.database!!.getBind(targetId)
                        if (wl.isNullOrEmpty()) {
                            sendBotMsg(groupId, replacePlaceholders(plugin.messages.qqEmptyBind, groupId = groupId, userId = targetId))
                            return
                        }
                        val msg = replacePlaceholders(
                            plugin.messages.adminQueryQQBind,
                            groupId = groupId,
                            originId = userId,
                            userId = targetId,
                            num = wl.size,
                            current = wl.toString()
                        )
                        sendBotMsg(groupId, msg)
                        return
                    }
                }
            }
            
            val wl = plugin.database!!.getBind(userId)
            if (wl.isNullOrEmpty()) {
                sendBotMsg(groupId, replacePlaceholders(plugin.messages.qqEmptyBind, groupId = groupId, userId = userId))
                return
            }
            
            val msg = replacePlaceholders(
                plugin.messages.playerQueryBind,
                groupId = groupId,
                userId = userId,
                num = wl.size,
                current = wl.toString()
            )
            sendBotMsg(groupId, msg)
        } catch (exception: Exception) {
            sendBotMsg(groupId, plugin.messages.wrongUsage)
        }
    }

    private fun onWhitelistRemove(message: String, groupId: Long, userId: Long) {
        try {
            if (isAdmin(userId)) {
                val subMsg = message.trim()
                when {
                    subMsg.startsWith("id:") -> {
                        val playerName = subMsg.substring(3).trim()
                        if (!WhitelistHelper.getInstance(plugin).checkPlayerExists(playerName)) {
                            sendBotMsg(groupId, replacePlaceholders(plugin.messages.notExistsBind, player = playerName))
                            return
                        }
                        val boundUserId = plugin.database!!.getBind(playerName)!!
                        plugin.database!!.removeBind(playerName)
                        kickPlayer(playerName)
                        
                        val wl = plugin.database!!.getBind(boundUserId) ?: LinkedHashMap()
                        val msg = replacePlaceholders(
                            plugin.messages.adminDeleteBind,
                            groupId = groupId,
                            originId = userId,
                            userId = boundUserId,
                            player = playerName,
                            num = wl.size,
                            current = wl.keys.toString()
                        )
                        sendBotMsg(groupId, msg)
                        return
                    }
                    subMsg.startsWith("qq:") -> {
                        val args = subMsg.substring(3).trim().split(" ")
                        val targetId = args[0].toLong()
                        val bindings = plugin.database?.getBind(targetId)
                        
                        if (bindings.isNullOrEmpty()) {
                            sendBotMsg(groupId, replacePlaceholders(plugin.messages.qqEmptyBind, groupId = groupId, userId = targetId))
                            return
                        }

                        if (args.size == 1) {
                            plugin.database?.removeBind(targetId)
                            bindings.forEach { (playerName, _) -> kickPlayer(playerName) }
                            
                            val msg = replacePlaceholders(
                                plugin.messages.adminDeleteAllBind,
                                groupId = groupId,
                                originId = userId,
                                userId = targetId
                            )
                            sendBotMsg(groupId, msg)
                        } else {
                            val targetPlayer = plugin.database!!.removeBindByNum(targetId, args[1].toInt())
                            targetPlayer?.let { kickPlayer(it) }
                            
                            val wl = plugin.database!!.getBind(targetId) ?: LinkedHashMap()
                            val msg = replacePlaceholders(
                                plugin.messages.adminDeleteBind,
                                groupId = groupId,
                                originId = userId,
                                userId = targetId,
                                player = targetPlayer.orEmpty(),
                                num = wl.size,
                                current = wl.keys.toString()
                            )
                            sendBotMsg(groupId, msg)
                        }
                        return
                    }
                }
            }

            // User logic
            try {
                val index = message.toInt()
                val targetPlayer = plugin.database!!.removeBindByNum(userId, index)
                targetPlayer?.let { kickPlayer(it) }
                
                val wl = plugin.database!!.getBind(userId) ?: LinkedHashMap()
                val msg = replacePlaceholders(
                    plugin.messages.playerDeleteBind,
                    groupId = groupId,
                    userId = userId,
                    player = targetPlayer.orEmpty(),
                    num = wl.size,
                    current = wl.keys.toString()
                )
                sendBotMsg(groupId, msg)
            } catch (_: NumberFormatException) {
                if (!WhitelistHelper.getInstance(plugin).checkPlayerBelongToUser(message, userId)) {
                    sendBotMsg(groupId, replacePlaceholders(plugin.messages.notBelongToYou, player = message))
                    return
                }
                plugin.database!!.removeBind(message)
                kickPlayer(message)
                
                val wl = plugin.database!!.getBind(userId) ?: LinkedHashMap()
                val msg = replacePlaceholders(
                    plugin.messages.playerDeleteBind,
                    groupId = groupId,
                    userId = userId,
                    player = message,
                    num = wl.size,
                    current = wl.keys.toString()
                )
                sendBotMsg(groupId, msg)
            }
        } catch (exception: Exception) {
            sendBotMsg(groupId, plugin.messages.wrongUsage)
        }
    }

    private fun onPlayerList(message: String, groupId: Long, userId: Long) {
        val newLine = 5
        var list = Universe.get().players.stream().map { it.username }.toList()
        var result = "\n"
        while (list.size > newLine) {
            result += list.slice(0..<newLine).joinToString(postfix = "\n  ")
            list = list.drop(newLine)
        }
        result += list.joinToString()
        result += "\n"

        val msg = replacePlaceholders(
            plugin.messages.playerList,
            playerList = result,
            playerNum = list.size
        )
        sendBotMsg(groupId, msg, "list")
    }

    private fun onRemoteCommand(message: String, groupId: Long, userId: Long) {
        if (!isAdmin(userId)) return
        runTaskAsync {
            val result = CommandDispatcher().dispatch(message)
            sendBotMsg(groupId, result, "cmd")
        }
    }

    fun onUserDecrease(event: GroupMemberDecreaseEvent) {
        runTaskAsync {
            plugin.database!!.getBind(event.userId)?.keys?.forEach { playerName ->
                kickPlayer(playerName)
            }
            plugin.database!!.removeBind(event.userId)
        }
    }
}