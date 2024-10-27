package me.regadpole.plumbot.listener

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.internal.WhitelistHelper

object GPlatformListener {
    private fun patchCMD(command: String):String {
        return PlumBot.getPlatformImpl().dispatchCommand(command)
    }
    fun onGroupMessage(message: String, groupId: String, senderId: String, senderName: String) {
        if (!PlumBot.getConfig().getConfig().groups.enableGroups.contains(groupId)) return
        if (message.startsWith(PlumBot.getConfig().getConfig().groups.prefix ?: "/")) {
            if (message.substring(1) == "help" || message.substring(1) == "帮助") {
                val sbuilder = StringBuilder()
                PlumBot.getLangConfig().getLangConf().help.forEach {
                    sbuilder.append(it.replace("prefix", PlumBot.getConfig().getConfig().groups.prefix!!))
                }
                PlumBot.getBot().sendGroupMsg(groupId, sbuilder.toString())
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.tps && message.substring(1) == "tps") {
                val tps = PlumBot.getPlatformImpl().getTPS()
                PlumBot.getBot().sendGroupMsg(groupId, PlumBot.getLangConfig().getLangConf().tps!!
                    .replace("%tps%", tps[0].toString())
                    .replace("%mspt%", tps[1].toString()))
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.online && message.substring(1) == "在线人数" || message.substring(1) == "list") {
                PlumBot.getBot().sendGroupMsg(groupId, PlumBot.getLangConfig().getLangConf().online!!
                    .replace("%count%", PlumBot.playerList.size.toString())
                    .replace("%player_list%", PlumBot.playerList.joinToString(", ")))
                return
            }
            if (PlumBot.getConfig().getConfig().groups.botAdmins.contains(groupId) && message.substring(1).startsWith("cmd")) {
                val cmd = message.substring(5)
                val result = patchCMD(cmd)
                PlumBot.getBot().sendPictureWithText(true, groupId, result)
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1).startsWith("申请白名单") &&
                message.split(" ").size == 2) {
//                val player = message.substring(6)
//                if (!WhitelistHelper.checkCount(senderId)) {
//                    PlumBot.getBot().sendGroupMsg(
//                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.fullBind!!
//                        .replace("%player_name%", player)
//                        .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
//                        .replace("%platform_id%", senderId)
//                        .replace("%max_limit%", PlumBot.getConfig().getConfig().groups.forwarding.whitelist.maxCount.toString()))
//                    return
//                }
//                if (!WhitelistHelper.checkIDNotExist(player)) {
//                    PlumBot.getBot().sendGroupMsg(
//                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.existsBind!!
//                        .replace("%player_name%", player))
//                    return
//                }
//                WhitelistHelper.addAndGet(senderId, player)
//                PlumBot.getBot().sendGroupMsg(
//                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
//                    .replace("%player_name%", player)
//                    .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
//                    .replace("%platform_id%", senderId))
//                return
                applyWhiteList(message.substring(6), senderId, groupId, true)
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1) == "移除白名单" &&
                message.split(" ").size == 2) {
//                val player = message.substring(6)
//                if (WhitelistHelper.checkIDNotExist(player)) {
//                    PlumBot.getBot().sendGroupMsg(
//                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.notExistsBind!!
//                        .replace("%player_name%", player))
//                    return
//                }
//                if (!WhitelistHelper.checkIDBelong(senderId, player)) {
//                    PlumBot.getBot().sendGroupMsg(
//                        groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.notBelongToYou!!
//                        .replace("%player_name%", player))
//                    return
//                }
//                PlumBot.getDatabase().removeBindByName(player)
//                PlumBot.getBot().sendGroupMsg(
//                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
//                    .replace("%player_name%", player)
//                    .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
//                    .replace("%platform_id%", senderId))
//                return
                deleteWhiteList(message.substring(6), senderId, groupId, true)
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.whitelist.enable && message.substring(1) == "查询白名单") {
//                PlumBot.getBot().sendGroupMsg(
//                    groupId, PlumBot.getLangConfig().getLangConf().whitelist.user.queryBind!!
//                        .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", ")))
//                return
                queryWhiteList(senderId, groupId, true)
                return
            }
            if (PlumBot.getConfig().getConfig().groups.forwarding.sdc) {
                PlumBot.getConfig().getCommands().adminCommand.forEach {
                    if (it.key == message.substring(1) && PlumBot.getConfig().getConfig().groups.botAdmins.
                        contains(senderId)) {
                        val command = it.value
                        val result = patchCMD(command)
                        PlumBot.getBot().sendPictureWithText(true, groupId, result)
                        return
                    }
                }
                PlumBot.getConfig().getCommands().userCommand.forEach {
                    if (it.key == message.substring(1)) {
                        val command = it.value
                        val result = patchCMD(command)
                        PlumBot.getBot().sendPictureWithText(true, groupId, result)
                        return
                    }
                }
            }
        }
        if (PlumBot.getConfig().getConfig().groups.forwarding.sdr) {
            PlumBot.getConfig().getReturns().adminReturn.forEach {
                if (it.key == message && PlumBot.getConfig().getConfig().groups.botAdmins.contains(senderId)) {
                    PlumBot.getBot().sendGroupMsg(groupId, it.value)
                    return
                }
            }
            PlumBot.getConfig().getReturns().userReturn.forEach {
                if (it.key == message) {
                    PlumBot.getBot().sendGroupMsg(groupId, it.value)
                    return
                }
            }
        }
        if (PlumBot.getConfig().getConfig().groups.forwarding.message.mode == 0) {
            PlumBot.getBot().sendGroupMsg(
                groupId, PlumBot.getLangConfig().getLangConf().forwarding.toServer!!
                .replace("%group_name%", PlumBot.getBot().getGroupName(groupId)?: groupId)
                .replace("%message%", message)
                .replace("%platform_name%", senderName))
            return
        } else if (PlumBot.getConfig().getConfig().groups.forwarding.message.mode == 1 &&
            message.startsWith(PlumBot.getConfig().getConfig().groups.forwarding.message.prefix!!)) {
            PlumBot.getBot().sendGroupMsg(
                groupId, PlumBot.getLangConfig().getLangConf().forwarding.toServer!!
                .replace("%group_name%", PlumBot.getBot().getGroupName(groupId)?: groupId)
                .replace("%message%", message.replace(PlumBot.getConfig().getConfig().groups.forwarding.message.prefix!!, ""))
                .replace("%platform_name%", senderName))
            return
        }
    }
    private fun applyWhiteList(player: String, senderId: String, targetId: String, isGroup: Boolean){
            if (!WhitelistHelper.checkCount(senderId)) {
                PlumBot.getBot().sendMsg(isGroup,
                    targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.fullBind!!
                        .replace("%player_name%", player)
                        .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                        .replace("%platform_id%", senderId)
                        .replace("%max_limit%", PlumBot.getConfig().getConfig().groups.forwarding.whitelist.maxCount.toString()))
                return
            }
            if (!WhitelistHelper.checkIDNotExist(player)) {
                PlumBot.getBot().sendMsg(isGroup,
                    targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.existsBind!!
                        .replace("%player_name%", player))
                return
            }
            WhitelistHelper.addAndGet(senderId, player)
            PlumBot.getBot().sendMsg(isGroup,
                targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
                    .replace("%player_name%", player)
                    .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                    .replace("%platform_id%", senderId))
            return
    }
    private fun deleteWhiteList(player: String, senderId: String, targetId: String, isGroup: Boolean) {
        if (WhitelistHelper.checkIDNotExist(player)) {
            PlumBot.getBot().sendMsg(isGroup,
                targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.notExistsBind!!
                    .replace("%player_name%", player))
            return
        }
        if (!WhitelistHelper.checkIDBelong(senderId, player)) {
            PlumBot.getBot().sendMsg(isGroup,
                targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.notBelongToYou!!
                    .replace("%player_name%", player))
            return
        }
        PlumBot.getDatabase().removeBindByName(player)
        PlumBot.getBot().sendMsg(isGroup,
            targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.addBind!!
                .replace("%player_name%", player)
                .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", "))
                .replace("%platform_id%", senderId))
        return
    }
    private fun queryWhiteList(senderId: String, targetId: String, isGroup: Boolean) {
        PlumBot.getBot().sendMsg(isGroup,
            targetId, PlumBot.getLangConfig().getLangConf().whitelist.user.queryBind!!
                .replace("%whitelist_name%", PlumBot.getDatabase().getBind(senderId).values.joinToString(", ")))
        return
    }
}
