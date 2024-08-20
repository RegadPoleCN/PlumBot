package me.regadpole.plumbot.bot

import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import love.forte.simbot.application.Application
import love.forte.simbot.application.launchApplication
import love.forte.simbot.application.listeners
import love.forte.simbot.common.id.ID
import love.forte.simbot.common.id.LongID.Companion.ID
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBot
import love.forte.simbot.component.onebot.v11.core.bot.OneBotBotConfiguration
import love.forte.simbot.component.onebot.v11.core.bot.firstOneBotBotManager
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotGroupMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.message.OneBotPrivateMessageEvent
import love.forte.simbot.component.onebot.v11.core.event.notice.OneBotGroupMemberDecreaseEvent
import love.forte.simbot.component.onebot.v11.core.useOneBot11
import love.forte.simbot.core.application.Simple
import love.forte.simbot.event.EventResult
import love.forte.simbot.event.process
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.listener.qq.QQListener
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submitAsync


class QQBot(private val plugin: PlumBot): Bot {

    private lateinit var application: Application

    private lateinit var onebot: OneBotBot

    /**
     * Start a bot
     */
    override fun start(): Bot {
        runBlocking {
            launch {
                application = launchApplication(Simple) {
                    useOneBot11()
                }

                application.configure()
                application.join()
            }
        }
        val groups: List<String> = plugin.getConfig().getConfig().enableGroups
        for (groupID in groups) {
            sendMsg(true, groupID, "PlumBot已启动")
        }
        info("已启动go-cqhttp服务")
        return this
    }

    private suspend fun Application.configure() {
        val botManager = botManagers.firstOneBotBotManager()
        onebot = botManager.register(
            OneBotBotConfiguration().apply {
                botUniqueId = "11112222"
                apiServerHost = Url("http://127.0.0.1:3000")
                eventServerHost = Url("ws://127.0.0.1:3001")
                accessToken = null
                /// ...
            }
        )
        listeners {
            process<OneBotPrivateMessageEvent> { event ->
                QQListener.onPrivateMessage(event)
                EventResult.empty()
            }
            process<OneBotGroupMessageEvent> { event ->
                QQListener.onGroupMessage(event)
                EventResult.empty()
            }
            process<OneBotGroupMemberDecreaseEvent> { event ->
                QQListener.onGroupMemberDecreased(event)
                EventResult.empty()
            }
        }
        onebot.start()
    }

    /**
     * Stop a bot
     */
    override fun shutdown() {
        val groups: List<String> = plugin.getConfig().getConfig().enableGroups
        for (groupID in groups) {
            sendMsg(true, groupID, "PlumBot已关闭")
        }
        application.cancel()
        info("已关闭go-cqhttp服务")
    }

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    override fun getGroupName(groupId: String): String? {
        if (groupId.toLongOrNull() == null) return null
        val group = groupId.toLong()
        application.botManagers.firstBot()
        return onebot.groupRelation.getGroup(group.ID)?.name
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
        if (onebot.groupRelation.getGroup(group.ID)?.getMember(user.ID) == null) return false
        return true
    }

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendGroupMsg(targetId: String, message: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.groupRelation.getGroup(target.ID)?.sendAsync(message)
    }

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    override fun sendUserMsg(targetId: String, message: String) {
        if (targetId.toLongOrNull() == null) return
        val target = targetId.toLong()
        onebot.contactRelation.getContact(target.ID)?.sendAsync(message)
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
        submitAsync(now = true) {
            if (isGroup) {
                sendGroupMsg(targetId, message)
            } else {
                sendUserMsg(targetId, message)
            }
        }
    }

    fun sendCQMsg(isGroup: Boolean, targetId: String, msg: String?) {
        if (msg == null || "" == msg) {
            return
        }
        submitAsync(now = true) {
            if (isGroup) {
                sendGroupMsg(targetId, msg)
            } else {
                sendUserMsg(targetId, msg)
            }
        }
    }
}