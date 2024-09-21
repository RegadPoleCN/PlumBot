package me.regadpole.plumbot.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.internal.WhitelistHelper
import taboolib.common5.util.replace

object GPlatformListener {
    fun onGroupMessage(message: String, groupId: String, senderId: String, senderName: String) {
        if (!PlumBot.getConfig().getConfig().groups.enableGroups.contains(groupId)) return
        if (message.startsWith(PlumBot.getConfig().getConfig().groups.prefix ?: "/")) {
            if (PlumBot.getConfig().getConfig().groups.forwarding.sdc) {
                PlumBot.getConfig().getCommands().adminCommand.forEach {
                    if (it.key == message.substring(1) && PlumBot.getConfig().getConfig().groups.botAdmins.
                        contains(senderId)) {
                        // TODO: 执行配置文件内游戏命令并返回
                    }
                }
                PlumBot.getConfig().getCommands().userCommand.forEach {
                    if (it.key == message.substring(1)) {
                        // TODO: 执行配置文件内游戏命令并返回
                    }
                }
            }
            if (message.substring(1) == "help" || message.substring(1) == "帮助") {

            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.tps && message.substring(1) == "tps") {
                // TODO: 获取服务器 TPS 并返回
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.online && message.substring(1) == "在线人数") {
                // TODO: 看看这样写有没有问题((
                PlumBot.getBot().sendGroupMsg(groupId, "在线玩家: ${PlumBot.playerList.joinToString(", ")}")
            }
            if (PlumBot.getConfig().getConfig().groups.botAdmins.contains(groupId) && message.substring(1) == "cmd") {
                // TODO: 执行message内游戏命令并返回
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1) == "申请白名单" &&
                message.split(" ").size == 2) {
                val player = message.substring(6)
                if (!WhitelistHelper.checkCount(senderId)) {
                    PlumBot.getBot().sendGroupMsg(
                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.fullBind!!
                        .replace("%player_name%", player)
                        .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                        .replace("%platform_id%", senderId)
                        .replace("%max_limit%", PlumBot.getConfig().getConfig().groups.forwarding.whitelist.maxCount.toString()))
                }
                if (!WhitelistHelper.checkIDNotExist(player)) {
                    PlumBot.getBot().sendGroupMsg(
                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.existsBind!!
                        .replace("%player_name%", player))
                }
                WhitelistHelper.addAndGet(senderId, player)
                PlumBot.getBot().sendGroupMsg(
                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
                    .replace("%player_name%", player)
                    .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                    .replace("%platform_id%", senderId))
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1) == "移除白名单" &&
                message.split(" ").size == 2) {
                val player = message.substring(6)
                if (WhitelistHelper.checkIDNotExist(player)) {
                    PlumBot.getBot().sendGroupMsg(
                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.notExistsBind!!
                        .replace("%player_name%", player))
                }
                if (!WhitelistHelper.checkIDBelong(senderId, player)) {
                    PlumBot.getBot().sendGroupMsg(
                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.notBelongToYou!!
                        .replace("%player_name%", player))
                }
                PlumBot.getDatabase().removeBindByName(player)
                PlumBot.getBot().sendGroupMsg(
                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
                    .replace("%player_name%", player)
                    .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                    .replace("%platform_id%", senderId))
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1) == "查询白名单") {
                PlumBot.getBot().sendGroupMsg(
                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.queryBind!!
                        .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", ")))
            }
        }
        if (PlumBot.getConfig().getConfig().groups.forwarding.sdr) {
            PlumBot.getConfig().getReturns().adminReturn.forEach {
                if (it.key == message && PlumBot.getConfig().getConfig().groups.botAdmins.contains(senderId)) {
                    PlumBot.getBot().sendGroupMsg(groupId, it.value)
                }
            }
            PlumBot.getConfig().getReturns().userReturn.forEach {
                if (it.key == message) {
                    PlumBot.getBot().sendGroupMsg(groupId, it.value)
                }
            }
        }
        if (PlumBot.getConfig().getConfig().groups.forwarding.message.mode == 0) {
            PlumBot.getBot().sendGroupMsg(
                groupId, PlumBot.getLangConfig().getLangConf().forwarding.toServer!!
                .replace("%group_name%", PlumBot.getBot().getGroupName(groupId)?: groupId)
                .replace("%message%", message)
                .replace("%platform_name%", senderName))
        } else if (PlumBot.getConfig().getConfig().groups.forwarding.message.mode == 1 &&
            message.startsWith(PlumBot.getConfig().getConfig().groups.forwarding.message.prefix!!)) {
            PlumBot.getBot().sendGroupMsg(
                groupId, PlumBot.getLangConfig().getLangConf().forwarding.toServer!!
                .replace("%group_name%", PlumBot.getBot().getGroupName(groupId)?: groupId)
                .replace("%message%", message.replace(PlumBot.getConfig().getConfig().groups.forwarding.message.prefix!!, ""))
                .replace("%platform_name%", senderName))
        }
    }
}
