package me.regadpole.plumbot.api.bot

import top.alazeprt.aonebot.client.websocket.WebsocketBotClient

interface IBot: Cloneable {

    val client:WebsocketBotClient

    /**
     * Start a bot
     */
    fun start(): IBot

    /**
     * Stop a bot
     */
    fun shutdown()

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    fun getGroupName(groupId: Long): String

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    fun checkUserInGroup(userId: Long, groupId: Long): Boolean

    /**
     * Send message to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean)

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupMsg(targetId: Long, message: String)

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserMsg(targetId: Long, message: String)

    /**
     * Send picture to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    fun sendPictureWithText(isGroup: Boolean, targetId: Long, message: String?)

    /**
     * Send a picture to group
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupPicWithText(targetId: Long, message: String)

    /**
     * Send a picture to user
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserPicWithText(targetId: Long, message: String)

    /**
     * Get user's name from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the name of user
     */
    fun getGroupUserName(groupId: Long, targetId: Long): String

    /**
     * Get user's card from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the card of user in group
     */
    fun getGroupUserCard(groupId: Long, targetId: Long): String

    public override fun clone(): IBot {
        return super.clone() as IBot
    }
}