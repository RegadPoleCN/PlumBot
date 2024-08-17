package me.regadpole.plumbot.bot

import cn.evole.onebot.client.OneBotClient
import cn.evole.onebot.client.core.BotConfig
import cn.evole.onebot.sdk.util.MsgUtils
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.listener.qq.QQListener


object QQBot: Bot {

    @JvmStatic
    lateinit var onebot: OneBotClient

    /**
     * Start a bot
     */
    override fun start(): Bot {
        onebot = OneBotClient.create(BotConfig("ws://127.0.0.1:8080")) //创建websocket客户端
            .open() //连接onebot服务端
            .registerEvents(QQListener()) //注册事件监听器
        val groups: List<String> = Config.getGroupQQs()
        for (groupID in groups) {
            sendMsg(true, groupID, "PlumBot已启动")
        }
        return this
    }

    /**
     * Stop a bot
     */
    override fun shutdown() {
        val groups: List<String> = Config.getGroupQQs()
        for (groupID in groups) {
            sendMsg(true, groupID, "PlumBot已关闭")
        }
        onebot.close()
    }

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    override fun getGroupName(groupId: String): String? {
        if (groupId.toLongOrNull() == null) return null
        val group = groupId.toLong()
        return onebot.bot.getGroupInfo(group, false).data.groupName
    }

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    override fun checkUserInGroup(userId: String, groupId: String): Boolean {
        if (userId.toLongOrNull() == null || groupId.toLongOrNull() == null) return false
        val user = userId.toLong(); val group = groupId.toLong()
        for (groupMemberInfoResp in onebot.bot.getGroupMemberList(group).data) {
            if (groupMemberInfoResp.userId == user) return true
        }
        return false
    }

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendGroupMsg(targetId: String, message: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.bot.sendGroupMsg(target, MsgUtils.builder().text(message).build(), true)
    }

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendUserMsg(targetId: String, message: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.bot.sendGroupMsg(target, MsgUtils.builder().text(message).build(), true)
    }

    /**
     * Send message to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    override fun sendMsg(isGroup: Boolean, targetId: String, message: String?) {
        if (message == null || "" == message) {
            return
        }
        PlumBot.getScheduler().runTaskAsynchronously {
            if (isGroup) {
                this.sendGroupMsg(targetId, message)
            } else {
                this.sendUserMsg(targetId, message)
            }
        }
    }

    fun sendCQMsg(isGroup: Boolean, targetId: String, msg: String?) {
        if (msg == null || "" == msg) {
            return
        }
        PlumBot.getScheduler().runTaskAsynchronously {
            if (isGroup) {
                this.sendGroupCQMsg(targetId, msg)
            } else {
                this.sendUserCQMsg(targetId, msg)
            }
        }
    }

    /**
     * 发送私聊消息
     */
    fun sendUserCQMsg(targetId: String, msg: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.bot.sendPrivateMsg(target, msg, false)
    }

    /**
     * 发送群聊消息
     */
    fun sendGroupCQMsg(targetId: String, msg: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.bot.sendGroupMsg(target, msg, false)
    }
}