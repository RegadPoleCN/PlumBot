package me.regadpole.plumbot.listener

import com.hypixel.hytale.server.core.NameMatching
import com.hypixel.hytale.server.core.universe.Universe
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.internal.dispatcher.CommandDispatcher
import me.regadpole.plumbot.utils.WhitelistHelper
import me.regadpole.plumbot.utils.getMessageFromString
import me.regadpole.plumbot.utils.runTaskAsync
import top.alazeprt.aonebot.event.message.GroupMessageEvent
import top.alazeprt.aonebot.event.notice.GroupMemberDecreaseEvent

class BotHandler(private val plugin: PlumBot, private val bot: IBot) {
    fun onGroupMessage(event: GroupMessageEvent) {
//        val lock = Object()
        var message = ""

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

//        synchronized(lock) {
//            lock.wait()
        val keys = plugin.config!!.getConfigMaker("keys")
        val prefix = plugin.config!!.getStringFromConfig("feature", "cmdPrefix")
        keys.getStringListFromConfig("list").forEach {
            val regexString = """$prefix$it"""
            if (regexString.toRegex().matches(message) && plugin.config!!.getBooleanFromConfig(
                    "feature",
                    "list",
                    "enable"
                )
            ) {
                onPlayerList(message.replace("$prefix$it", ""), event.groupId, event.senderId)
                return
            }
        }
        keys.getStringListFromConfig("addBind").forEach {
            val regexString = """$prefix$it (.+)"""
            if (regexString.toRegex().matches(message) && plugin.config!!.getBooleanFromConfig(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistApply(message.replace("$prefix$it ", ""), event.groupId, event.senderId)
                return
            }
        }
        keys.getStringListFromConfig("deleteBind").forEach {
            val regexString = """$prefix$it (.+)"""
            if (regexString.toRegex().matches(message) && plugin.config!!.getBooleanFromConfig(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistRemove(message.replace("$prefix$it ", ""), event.groupId, event.senderId)
                return
            }
        }
        keys.getStringListFromConfig("queryBind").forEach {
            val regexString = """$prefix$it(.*)"""
            if (regexString.toRegex().matches(message) && plugin.config!!.getBooleanFromConfig(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistQuery(message.replace("$prefix$it", ""), event.groupId, event.senderId)
                return
            }
        }
        keys.getStringListFromConfig("cmd").forEach {
            val regexString = """$prefix$it (.+)"""
            if (regexString.toRegex().matches(message) && plugin.config!!.getBooleanFromConfig(
                    "feature",
                    "cmd",
                    "enable"
                )
            ) {
                onRemoteCommand(message.replace("$prefix$it ", ""), event.groupId, event.senderId)
                return
            }
        }

        if (!plugin.config!!.getBooleanFromConfig("feature", "message", "enable")) return

        if (plugin.config!!.getIntegerFromConfig("feature", "message", "mode") == 0) {
            Universe.get().sendMessage(
                getMessageFromString(
                    // group_name, group_id, user_nick, message, user_id, user_name
                    plugin.messages.ob2server
                        .replace("%group_name%", bot.getGroupName(event.groupId))
                        .replace("%group_id%", event.groupId.toString())
                        .replace("%user_nick%", bot.getGroupUserCard(event.groupId, event.senderId))
                        .replace("%message%", message)
                        .replace("%user_id%", event.senderId.toString())
                        .replace(
                            "%user_name%",
                            bot.getGroupUserName(event.groupId, event.senderId)
                        )
                )
            )
            return
        } else if (plugin.config!!.getIntegerFromConfig("feature", "message", "mode") == 1 &&
            Regex(
                plugin.config!!.getStringFromConfig(
                    "feature",
                    "message",
                    "prefix"
                )!!
            ).matchesAt(message, 0)
        ) {
            Universe.get().sendMessage(
                getMessageFromString(
                    plugin.messages.ob2server
                        // group_name, group_id, user_nick, message, user_id, user_name
                        .replace("%group_name%", bot.getGroupName(event.groupId))
                        .replace("%group_id%", event.groupId.toString())
                        .replace("%user_nick%", bot.getGroupUserCard(event.groupId, event.senderId))
                        .replace("%message%", message.replace(plugin.config!!.getStringFromConfig("feature", "message", "prefix")!!, ""))
                        .replace("%user_id%", event.senderId.toString())
                        .replace(
                            "%user_name%",
                            bot.getGroupUserName(event.groupId, event.senderId)
                        )
                )
            )
            return
        }
//        }
    }

    private fun onWhitelistApply(message: String, groupId: Long, userId: Long) {
        try {
            if (plugin.config!!.getLongListFromConfig("admins").contains(userId)) {
                val args = message.split(" ")
                when (args.size) {
                    1 -> {
                        if (WhitelistHelper.getInstance(plugin).checkPlayerExists(message)) {
                            val msg = plugin.messages.existsBind
//                        # player_name
                                .replace("%player_name%", message)
                            bot.sendMsg(
                                true,
                                groupId,
                                msg,
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }
                        plugin.database!!.addBind(userId, message)
                        val wl = plugin.database!!.getBind(userId)!!
//                    # user_nick, user_name, user_id, target_player, num, current
                        bot.sendMsg(
                            true,
                            groupId,
                            plugin.messages.playerAddBind
                                .replace("%user_id%", userId.toString())
                                .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                                .replace("%target_player%", message)
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.keys.toString()),
                            plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                        )
                        return
                    }

                    2 -> {
                        if (WhitelistHelper.getInstance(plugin).checkPlayerExists(args[1])) {
                            val msg = plugin.messages.existsBind
//                        # player_name
                                .replace("%player_name%", args[1])
                            bot.sendMsg(
                                true,
                                groupId,
                                msg,
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }
                        plugin.database!!.addBind(args[0].toLong(), args[1])
                        val wl = plugin.database!!.getBind(args[0].toLong())!!
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                            plugin.messages.adminAddBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", args[0])
                                .replace("%user_name%", bot.getGroupUserName(groupId, args[0].toLong()))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, args[0].toLong()))
                                .replace("%target_player%", args[1])
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.keys.toString()),
                            plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                        )
                        return
                    }
                }
            } else {
                if (WhitelistHelper.getInstance(plugin).checkUserBindingFull(userId)) {
                    val wl = plugin.database!!.getBind(userId)!!
                    val msg = plugin.messages.fullBind
//                        # player_name, current, user_id, whitelist_limit, user_name, user_nick
                        .replace("%player_name%", wl.keys.toString())
                        .replace("%current%", wl.size.toString())
                        .replace("%user_id%", userId.toString())
                        .replace(
                            "%whitelist_limit%",
                            plugin.config!!.getIntegerFromConfig("feature", "bind", "maxNum").toString()
                        )
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                    bot.sendMsg(true, groupId, msg, plugin.config!!.getBooleanFromConfig("feature", "bind", "pic"))
                    return
                }
                if (WhitelistHelper.getInstance(plugin).checkPlayerExists(message)) {
                    val msg = plugin.messages.existsBind
//                        # player_name
                        .replace("%player_name%", message)
                    bot.sendMsg(true, groupId, msg, plugin.config!!.getBooleanFromConfig("feature", "bind", "pic"))
                    return
                }
                plugin.database!!.addBind(userId, message)
                val wl = plugin.database!!.getBind(userId)!!
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    plugin.messages.playerAddBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", message)
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                )
                return
            }
        } catch (exception: Exception) {
            bot.sendMsg(
                true,
                groupId,
                plugin.messages.wrongUsage,
                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
            )
            return
        }
    }

    private fun onWhitelistQuery(message: String, groupId: Long, userId: Long) {
        try {
            if (message.findAnyOf(listOf(" ")) != null && plugin.config!!.getLongListFromConfig("admins")
                    .contains(userId)
            ) {
                val msg = message.substring(1)
                when (msg.substring(0..2)) {
                    "id:" -> {
                        val arg = msg.substring(3)
                        val wl = plugin.database!!.getBind(arg)
                        if (wl == null) {
                            bot.sendMsg(
                                true,
                                groupId,
                                plugin.messages.idEmptyBind
                                    .replace("%player%", arg),
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, player
                            plugin.messages.adminQueryIdBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", wl.toString())
                                .replace("%user_name%", bot.getGroupUserName(groupId, wl.toLong()))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, wl.toLong()))
                                .replace("%player%", arg),
                            plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                        )
                        return
                    }

                    "qq:" -> {
                        val arg = msg.substring(3).toLong()
                        val wl = plugin.database!!.getBind(arg)
                        if (wl.isNullOrEmpty()) {
                            bot.sendMsg(
                                true,
                                groupId,
//                            # user_id, user_name, user_nick
                                plugin.messages.qqEmptyBind
                                    .replace("%user_id%", arg.toString())
                                    .replace("%user_name%", bot.getGroupUserName(groupId, arg))
                                    .replace("%user_nick%", bot.getGroupUserCard(groupId, arg)),
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, num, current
                            plugin.messages.adminQueryQQBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", arg.toString())
                                .replace("%user_name%", bot.getGroupUserName(groupId, arg))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, arg))
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.toString()),
                            plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                        )
                        return
                    }
                }
            }
            val wl = plugin.database!!.getBind(userId)
            if (wl.isNullOrEmpty()) {
                bot.sendMsg(
                    true,
                    groupId,
                    plugin.messages.qqEmptyBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId)),
                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                )
                return
            }
            bot.sendMsg(
                true, groupId,
//            # user_nick, user_name, user_id, num, current
                plugin.messages.playerQueryBind
                    .replace("%user_id%", userId.toString())
                    .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                    .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                    .replace("%num%", wl.size.toString())
                    .replace("%current%", wl.toString()), plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
            )
            return
        } catch (exception: Exception) {
            bot.sendMsg(
                true,
                groupId,
                plugin.messages.wrongUsage,
                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
            )
            return
        }
    }

    private fun onWhitelistRemove(message: String, groupId: Long, userId: Long) {
        try {
            if (plugin.config!!.getLongListFromConfig("admins").contains(userId)) {
                try {
                    when (message.substring(0..2)) {
                        "id:" -> {
                            val arg = message.substring(3)
                            if (!WhitelistHelper.getInstance(plugin).checkPlayerExists(arg)) {
                                val msg = plugin.messages.notExistsBind
//                        # player_name
                                    .replace("%player_name%", arg)
                                bot.sendMsg(
                                    true,
                                    groupId,
                                    msg,
                                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                                )
                                return
                            }
                            val target = plugin.database!!.getBind(arg)!!
                            plugin.database!!.removeBind(arg)
                            val player = Universe.get().getPlayerByUsername(arg, NameMatching.EXACT)
                            player?.packetHandler?.disconnect(
                                    plugin.messages.kickServer
                                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                            )
                            var wl = plugin.database!!.getBind(target)
                            if (wl.isNullOrEmpty()) wl = LinkedHashMap()
//                    # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                            bot.sendMsg(
                                true,
                                groupId,
                                plugin.messages.adminDeleteBind
                                    .replace("%origin_id%", userId.toString())
                                    .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                    .replace("%user_id%", target.toString())
                                    .replace("%user_name%", bot.getGroupUserName(groupId, target))
                                    .replace("%user_nick%", bot.getGroupUserCard(groupId, target))
                                    .replace("%target_player%", arg)
                                    .replace("%num%", wl.size.toString())
                                    .replace("%current%", wl.keys.toString()),
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }

                        "qq:" -> {
                            val arg = message.substring(3).split(" ")
                            if (arg.size == 1) {
                                val bindings = plugin.database?.getBind(arg[0].toLong())
                                if (bindings.isNullOrEmpty()) {
                                    val msg = plugin.messages.qqEmptyBind
//                        # user_id, user_name, user_nick
                                        .replace("%user_id%", arg[0])
                                        .replace(
                                            "%user_name%",
                                            bot.getGroupUserName(groupId, arg[0].toLong())
                                        )
                                        .replace(
                                            "%user_nick%",
                                            bot.getGroupUserCard(groupId, arg[0].toLong())
                                        )
                                    bot.sendMsg(
                                        true,
                                        groupId,
                                        msg,
                                        plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                                    )
                                    return
                                }

                                plugin.database?.removeBind(arg[0].toLong())

                                // 踢出所有绑定的在线玩家
                                bindings.forEach { (playerName, _) ->
                                    val player = Universe.get().getPlayerByUsername(playerName, NameMatching.EXACT)
                                    val kickMessage = plugin.messages.kickServer
                                        .replace("%groups%", plugin.config?.getLongListFromConfig("groups").toString())
                                    player?.packetHandler?.disconnect(kickMessage)
                                }

                                bot.sendMsg(
                                    true,
                                    groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                                    plugin.messages.adminDeleteAllBind
                                        .replace("%origin_id%", userId.toString())
                                        .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                        .replace("%user_id%", arg[0])
                                        .replace(
                                            "%user_name%",
                                            bot.getGroupUserName(groupId, arg[0].toLong())
                                        )
                                        .replace(
                                            "%user_nick%",
                                            bot.getGroupUserCard(groupId, arg[0].toLong())
                                        ),
                                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                                )
                                return
                            }
                            if (plugin.database!!.getBind(arg[0].toLong()).isNullOrEmpty()) {
                                val msg = plugin.messages.qqEmptyBind
//                        # user_id, user_name, user_nick
                                    .replace("%user_id%", arg[0])
                                    .replace(
                                        "%user_name%",
                                        bot.getGroupUserName(groupId, arg[0].toLong())
                                    )
                                    .replace(
                                        "%user_nick%",
                                        bot.getGroupUserCard(groupId, arg[0].toLong())
                                    )
                                bot.sendMsg(
                                    true,
                                    groupId,
                                    msg,
                                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                                )
                                return
                            }
                            val target = plugin.database!!.removeBindByNum(arg[0].toLong(), arg[1].toInt())
                            val player = target?.let { Universe.get().getPlayerByUsername(it, NameMatching.EXACT) }
                            player?.packetHandler?.disconnect(
                                    plugin.messages.kickServer
                                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                            )
                            var wl = plugin.database!!.getBind(arg[0].toLong())
                            if (wl.isNullOrEmpty()) wl = LinkedHashMap()
                            bot.sendMsg(
                                true,
                                groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                                plugin.messages.adminDeleteBind
                                    .replace("%origin_id%", userId.toString())
                                    .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                    .replace("%user_id%", arg[0])
                                    .replace(
                                        "%user_name%",
                                        bot.getGroupUserName(groupId, arg[0].toLong())
                                    )
                                    .replace(
                                        "%user_nick%",
                                        bot.getGroupUserCard(groupId, arg[0].toLong())
                                    )
                                    .replace("%target_player%", target.orEmpty())
                                    .replace("%num%", wl.size.toString())
                                    .replace("%current%", wl.keys.toString()),
                                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                            )
                            return
                        }
                    }
                } catch (_: Exception) {}
            }
            try {
                val target = plugin.database!!.removeBindByNum(userId, message.toInt())
                val player = target?.let { Universe.get().getPlayerByUsername(it, NameMatching.EXACT) }
                player?.packetHandler?.disconnect(
                    plugin.messages.kickServer
                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                )
                var wl = plugin.database!!.getBind(userId)
                if (wl.isNullOrEmpty()) wl = LinkedHashMap()
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    plugin.messages.playerDeleteBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", target.orEmpty())
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                )
            } catch (_: NumberFormatException) {
                if (!WhitelistHelper.getInstance(plugin).checkPlayerBelongToUser(message, userId)) {
                    val msg = plugin.messages.notBelongToYou
//                        # player_name
                        .replace("%player_name%", message)
                    bot.sendMsg(
                        true,
                        groupId,
                        msg,
                        plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                    )
                    return
                }
                plugin.database!!.removeBind(message)
                val player = Universe.get().getPlayerByUsername(message, NameMatching.EXACT)
                player?.packetHandler?.disconnect(
                    plugin.messages.kickServer
                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                )
                var wl = plugin.database!!.getBind(userId)
                if (wl.isNullOrEmpty()) wl = LinkedHashMap()
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    plugin.messages.playerDeleteBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", message)
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
                )
            }
            return
        } catch (exception: Exception) {
            bot.sendMsg(
                true,
                groupId,
                plugin.messages.wrongUsage,
                plugin.config!!.getBooleanFromConfig("feature", "bind", "pic")
            )
            return
        }
    }

    private fun onPlayerList(message: String, groupId: Long, userId: Long) {
        val newLine = 5
        var list = Universe.get().players.stream().map { it.username }.toList()
        var result = "\n"
        while(list.size > newLine) {
            result += list.slice(0..<newLine).joinToString(postfix = "\n  ")
            list = list.drop(newLine)
        }
        result += list.joinToString()
        result += "\n"

        bot.sendMsg(true, groupId,
//            # player_list, player_num, max_player
            plugin.messages.playerList
                .replace("%player_list%", result)
                .replace("%player_num%", list.size.toString()), plugin.config!!.getBooleanFromConfig("feature", "list", "pic"))
        return
    }

    private fun onRemoteCommand(message: String, groupId: Long, userId: Long) {
        runTaskAsync{
            val result = CommandDispatcher().dispatch(message)
            bot.sendMsg(true, groupId, result, plugin.config!!.getBooleanFromConfig("feature", "cmd", "pic"))
        }
        return
    }

    fun onUserDecrease(event: GroupMemberDecreaseEvent) {
        runTaskAsync {
            plugin.database!!.getBind(event.userId)?.keys?.forEach {
                val player = Universe.get().getPlayerByUsername(it, NameMatching.EXACT)
                player?.packetHandler?.disconnect(
                    plugin.messages.kickServer
                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
                )
            }
            plugin.database!!.removeBind(event.userId)
        }
    }
}