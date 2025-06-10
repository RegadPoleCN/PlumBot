package me.regadpole.plumbot.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.utils.WhitelistHelper
import me.regadpole.plumbot.utils.getComponentFromMiniMsg

class OneBotHandler(private val plugin: PlumBot, private val bot: IBot): BotHandler {
    override fun onGroupMessage(message: String, groupId: Long, userId: Long) {
//        val lock = Object()

//        synchronized(lock) {
//            lock.wait()
        val keys = plugin.config.getConfigMaker("keys")
        val prefix = plugin.config.getString("feature", "cmdPrefix")
        keys.getStringList("list").forEach {
            val regexString = """$prefix$it"""
            if (regexString.toRegex().matches(message) && plugin.config.getBoolean(
                    "feature",
                    "list",
                    "enable"
                )
            ) {
                onPlayerList(message.replace("$prefix$it", ""), groupId, userId)
                return
            }
        }
        keys.getStringList("addBind").forEach {
            val regexString = """$prefix$it (.+)"""
            if (regexString.toRegex().matches(message) && plugin.config.getBoolean(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistApply(message.replace("$prefix$it ", ""), groupId, userId)
                return
            }
        }
        keys.getStringList("deleteBind").forEach {
            val regexString = """$prefix$it (.+)"""
            if (regexString.toRegex().matches(message) && plugin.config.getBoolean(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistRemove(message.replace("$prefix$it ", ""), groupId, userId)
                return
            }
        }
        keys.getStringList("queryBind").forEach {
            val regexString = """$prefix$it(.*)"""
            if (regexString.toRegex().matches(message) && plugin.config.getBoolean(
                    "feature",
                    "bind",
                    "enable"
                )
            ) {
                onWhitelistQuery(message.replace("$prefix$it", ""), groupId, userId)
                return
            }
        }

        if (!plugin.config.getBoolean("feature", "message", "enable")) return

        if (plugin.config.getInteger("feature", "message", "mode") == 0) {
            plugin.sendMessage(
                getComponentFromMiniMsg(
                    // group_name, group_id, user_nick, message, user_id, user_name
                    Messages.ob2server
                        .replace("%group_name%", bot.getGroupName(groupId))
                        .replace("%group_id%", groupId.toString())
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%message%", message)
                        .replace("%user_id%", userId.toString())
                        .replace(
                            "%user_name%",
                            bot.getGroupUserName(groupId, userId)
                        )
                )
            )
            return
        } else if (plugin.config.getInteger("feature", "message", "mode") == 1 &&
            Regex(
                plugin.config.getString(
                    "feature",
                    "message",
                    "prefix"
                )!!
            ).matchesAt(message, 0)
        ) {
            plugin.sendMessage(
                getComponentFromMiniMsg(
                    Messages.ob2server
                        // group_name, group_id, user_nick, message, user_id, user_name
                        .replace("%group_name%", bot.getGroupName(groupId))
                        .replace("%group_id%", groupId.toString())
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%message%", message.replace(plugin.config.getString("feature", "message", "prefix")!!, ""))
                        .replace("%user_id%", userId.toString())
                        .replace(
                            "%user_name%",
                            bot.getGroupUserName(groupId, userId)
                        )
                )
            )
            return
        }
//        }
    }

    override fun onWhitelistApply(message: String, groupId: Long, userId: Long) {
        try {
            if (plugin.config.getLongList("admins").contains(userId)) {
                val args = message.split(" ")
                when (args.size) {
                    1 -> {
                        if (WhitelistHelper.getInstance(plugin).checkPlayerExists(message)) {
                            val msg = Messages.existsBind
//                        # player_name
                                .replace("%player_name%", message)
                            bot.sendMsg(
                                true,
                                groupId,
                                msg,
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }
                        DatabaseProvider.getDatabase()!!.addBind(userId, message)
                        val wl = DatabaseProvider.getBindByUser(userId.toString())
//                    # user_nick, user_name, user_id, target_player, num, current
                        bot.sendMsg(
                            true,
                            groupId,
                            Messages.playerAddBind
                                .replace("%user_id%", userId.toString())
                                .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                                .replace("%target_player%", message)
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.keys.toString()),
                            plugin.config.getBoolean("feature", "bind", "pic")
                        )
                        return
                    }

                    2 -> {
                        if (WhitelistHelper.getInstance(plugin).checkPlayerExists(args[1])) {
                            val msg = Messages.existsBind
//                        # player_name
                                .replace("%player_name%", args[1])
                            bot.sendMsg(
                                true,
                                groupId,
                                msg,
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }
                        DatabaseProvider.getDatabase()!!.addBind(args[0].toLong(), args[1])
                        val wl = DatabaseProvider.getBindByUser(args[0])
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                            Messages.adminAddBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", args[0])
                                .replace("%user_name%", bot.getGroupUserName(groupId, args[0].toLong()))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, args[0].toLong()))
                                .replace("%target_player%", args[1])
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.keys.toString()),
                            plugin.config.getBoolean("feature", "bind", "pic")
                        )
                        return
                    }
                }
            } else {
                if (WhitelistHelper.getInstance(plugin).checkUserBindingFull(userId.toString())) {
                    val wl = DatabaseProvider.getBindByUser(userId.toString())
                    val msg = Messages.fullBind
//                        # player_name, current, user_id, whitelist_limit, user_name, user_nick
                        .replace("%player_name%", wl.keys.toString())
                        .replace("%current%", wl.size.toString())
                        .replace("%user_id%", userId.toString())
                        .replace(
                            "%whitelist_limit%",
                            plugin.config.getInteger("feature", "bind", "maxNum").toString()
                        )
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                    bot.sendMsg(true, groupId, msg, plugin.config.getBoolean("feature", "bind", "pic"))
                    return
                }
                if (WhitelistHelper.getInstance(plugin).checkPlayerExists(message)) {
                    val msg = Messages.existsBind
//                        # player_name
                        .replace("%player_name%", message)
                    bot.sendMsg(true, groupId, msg, plugin.config.getBoolean("feature", "bind", "pic"))
                    return
                }
                DatabaseProvider.getDatabase()!!.addBind(userId, message)
                val wl = DatabaseProvider.getBindByUser(userId.toString())
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    Messages.playerAddBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", message)
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config.getBoolean("feature", "bind", "pic")
                )
                return
            }
        } catch (_: Exception) {
            bot.sendMsg(
                true,
                groupId,
                Messages.wrongUsage,
                plugin.config.getBoolean("feature", "bind", "pic")
            )
            return
        }
    }

    override fun onWhitelistQuery(message: String, groupId: Long, userId: Long) {
        try {
            if (message.findAnyOf(listOf(" ")) != null && plugin.config.getLongList("admins")
                    .contains(userId)
            ) {
                val msg = message.substring(1)
                when (msg.substring(0..2)) {
                    "id:" -> {
                        val arg = msg.substring(3)
                        val wl = DatabaseProvider.getBindByName(arg)
                        if (wl == null) {
                            bot.sendMsg(
                                true,
                                groupId,
                                Messages.idEmptyBind
                                    .replace("%player%", arg),
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, player
                            Messages.adminQueryIdBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", wl)
                                .replace("%user_name%", bot.getGroupUserName(groupId, wl.toLong()))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, wl.toLong()))
                                .replace("%player%", arg),
                            plugin.config.getBoolean("feature", "bind", "pic")
                        )
                        return
                    }

                    "qq:" -> {
                        val arg = msg.substring(3).toLong()
                        val wl = DatabaseProvider.getBindByUser(arg.toString())
                        if (wl.isEmpty()) {
                            bot.sendMsg(
                                true,
                                groupId,
//                            # user_id, user_name, user_nick
                                Messages.qqEmptyBind
                                    .replace("%user_id%", arg.toString())
                                    .replace("%user_name%", bot.getGroupUserName(groupId, arg))
                                    .replace("%user_nick%", bot.getGroupUserCard(groupId, arg)),
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }
                        bot.sendMsg(
                            true,
                            groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, num, current
                            Messages.adminQueryQQBind
                                .replace("%origin_id%", userId.toString())
                                .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                .replace("%user_id%", arg.toString())
                                .replace("%user_name%", bot.getGroupUserName(groupId, arg))
                                .replace("%user_nick%", bot.getGroupUserCard(groupId, arg))
                                .replace("%num%", wl.size.toString())
                                .replace("%current%", wl.keys.toString()),
                            plugin.config.getBoolean("feature", "bind", "pic")
                        )
                        return
                    }
                }
            }
            val wl = DatabaseProvider.getBindByUser(userId.toString())
            if (wl.isEmpty()) {
                bot.sendMsg(
                    true,
                    groupId,
                    Messages.qqEmptyBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId)),
                    plugin.config.getBoolean("feature", "bind", "pic")
                )
                return
            }
            bot.sendMsg(
                true, groupId,
//            # user_nick, user_name, user_id, num, current
                Messages.playerQueryBind
                    .replace("%user_id%", userId.toString())
                    .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                    .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                    .replace("%num%", wl.size.toString())
                    .replace("%current%", wl.toString()), plugin.config.getBoolean("feature", "bind", "pic")
            )
            return
        } catch (_: Exception) {
            bot.sendMsg(
                true,
                groupId,
                Messages.wrongUsage,
                plugin.config.getBoolean("feature", "bind", "pic")
            )
            return
        }
    }

    override fun onWhitelistRemove(message: String, groupId: Long, userId: Long) {
        try {
            if (plugin.config.getLongList("admins").contains(userId)) {
                try {
                    when (message.substring(0..2)) {
                        "id:" -> {
                            val arg = message.substring(3)
                            if (!WhitelistHelper.getInstance(plugin).checkPlayerExists(arg)) {
                                val msg = Messages.notExistsBind
//                        # player_name
                                    .replace("%player_name%", arg)
                                bot.sendMsg(
                                    true,
                                    groupId,
                                    msg,
                                    plugin.config.getBoolean("feature", "bind", "pic")
                                )
                                return
                            }
                            val target = DatabaseProvider.getBindByName(arg)!!.toLong()
                            DatabaseProvider.getDatabase()!!.removeBind(arg)
//                            val player = plugin.server.getPlayer(arg)
//                            if (player.isPresent) player.get().disconnect(
//                                getComponentFromMiniMsg(
//                                    Messages.kickServer
//                                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                                )
//                            )
                            plugin.kickPlayer(arg)
                            val wl = DatabaseProvider.getBindByUser(target.toString())
//                    # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                            bot.sendMsg(
                                true,
                                groupId,
                                Messages.adminDeleteBind
                                    .replace("%origin_id%", userId.toString())
                                    .replace("%origin_name%", bot.getGroupUserCard(groupId, userId))
                                    .replace("%user_id%", target.toString())
                                    .replace("%user_name%", bot.getGroupUserName(groupId, target))
                                    .replace("%user_nick%", bot.getGroupUserCard(groupId, target))
                                    .replace("%target_player%", arg)
                                    .replace("%num%", wl.size.toString())
                                    .replace("%current%", wl.keys.toString()),
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }

                        "qq:" -> {
                            val arg = message.substring(3).split(" ")
                            if (DatabaseProvider.getBindByUser(arg[0]).isEmpty()) {
                                val msg = Messages.qqEmptyBind
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
                                    plugin.config.getBoolean("feature", "bind", "pic")
                                )
                                return
                            }
                            val target = DatabaseProvider.getDatabase()!!.removeBindByNum(arg[0].toLong(), arg[1].toInt())
//                            val player = plugin.server.getPlayer(target)
//                            if (player.isPresent) player.get().disconnect(
//                                getComponentFromMiniMsg(
//                                    Messages.kickServer
//                                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                                )
//                            )
                            target?.let { plugin.kickPlayer(it) }
                            var wl = DatabaseProvider.getBindByUser(arg[0])
                            if (wl.isEmpty()) wl = LinkedHashMap()
                            bot.sendMsg(
                                true,
                                groupId,
//                        # origin_id, origin_name, user_id, user_name, user_nick, target_player, num, current
                                Messages.adminDeleteBind
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
                                    .replace("%target_player%", target!!)
                                    .replace("%num%", wl.size.toString())
                                    .replace("%current%", wl.keys.toString()),
                                plugin.config.getBoolean("feature", "bind", "pic")
                            )
                            return
                        }
                    }
                } catch (_: Exception) {}
            }
            try {
                val target = DatabaseProvider.getDatabase()!!.removeBindByNum(userId, message.toInt())
//                val player = plugin.server.getPlayer(target)
//                if (player.isPresent) player.get().disconnect(
//                    getComponentFromMiniMsg(
//                        Messages.kickServer
//                            .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                    ))
                target?.let { plugin.kickPlayer(it) }
                var wl = DatabaseProvider.getBindByUser(userId.toString())
                if (wl.isEmpty()) wl = LinkedHashMap()
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    Messages.playerDeleteBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", target!!)
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config.getBoolean("feature", "bind", "pic")
                )
            } catch (_: NumberFormatException) {
                if (!WhitelistHelper.getInstance(plugin).checkPlayerBelongToUser(message, userId.toString())) {
                    val msg = Messages.notBelongToYou
//                        # player_name
                        .replace("%player_name%", message)
                    bot.sendMsg(
                        true,
                        groupId,
                        msg,
                        plugin.config.getBoolean("feature", "bind", "pic")
                    )
                    return
                }
                DatabaseProvider.getDatabase()!!.removeBind(message)
//                val player = plugin.server.getPlayer(message)
//                if (player.isPresent) player.get().disconnect(
//                    getComponentFromMiniMsg(
//                        Messages.kickServer
//                            .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                    ))
                plugin.kickPlayer(message)
                var wl = DatabaseProvider.getBindByUser(userId.toString())
                if (wl.isEmpty()) wl = LinkedHashMap()
//                    # user_nick, user_name, user_id, target_player, num, current
                bot.sendMsg(
                    true,
                    groupId,
                    Messages.playerDeleteBind
                        .replace("%user_id%", userId.toString())
                        .replace("%user_name%", bot.getGroupUserName(groupId, userId))
                        .replace("%user_nick%", bot.getGroupUserCard(groupId, userId))
                        .replace("%target_player%", message)
                        .replace("%num%", wl.size.toString())
                        .replace("%current%", wl.keys.toString()),
                    plugin.config.getBoolean("feature", "bind", "pic")
                )
            }
            return
        } catch (_: Exception) {
            bot.sendMsg(
                true,
                groupId,
                Messages.wrongUsage,
                plugin.config.getBoolean("feature", "bind", "pic")
            )
            return
        }
    }

    override fun onPlayerList(message: String, groupId: Long, userId: Long) {
//        val newLine = 5
//        val list = plugin.server.allPlayers
//        val playerMap = mutableMapOf<RegisteredServer, List<String>>()
//        plugin.server.allServers.forEach {
//            playerMap[it] = it.playersConnected.map { player -> player.username }
//        }
//        var result = "\n"
//        playerMap.keys.forEach {
//            result += "${it.serverInfo.name}: "
////            val player = playerMap[it].orEmpty().forEach { player -> result += "${player.username}, " }
//            while(playerMap[it].orEmpty().size > newLine) {
//                result += playerMap[it].orEmpty().slice(0..<newLine).joinToString(postfix = "\n  ")
//                playerMap[it] = playerMap[it].orEmpty().drop(newLine)
//            }
//            result += playerMap[it].orEmpty().joinToString()
//            result += "\n"
//        }

        bot.sendMsg(true, groupId,
//            # player_list, player_num, max_player
            Messages.playerList
                .replace("%player_list%", plugin.listPlayerString())
                .replace("%player_num%", plugin.listPlayers().size.toString()), plugin.config.getBoolean("feature", "list", "pic"))
        return
    }

    override fun onUserDecrease(groupId: Long, userId: Long) {
        DatabaseProvider.getBindByUser(userId.toString()).keys.forEach {
//            val player = plugin.server.getPlayer(it)
//            if (player.isPresent) player.get().disconnect(
//                getComponentFromMiniMsg(
//                    Messages.kickServer
//                        .replace("%groups%", plugin.config!!.getLongListFromConfig("groups").toString())
//                ))
            plugin.kickPlayer(it)
        }
        DatabaseProvider.getDatabase()!!.removeBind(userId)
    }
}