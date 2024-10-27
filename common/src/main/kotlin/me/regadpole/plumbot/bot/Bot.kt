package me.regadpole.plumbot.bot

interface Bot {

    /**
     * Start a bot
     */
    fun start(): Bot

    /**
     * Stop a bot
     */
    fun shutdown()

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    fun getGroupName(groupId: String): String?

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    fun checkUserInGroup(userId: String, groupId: String): Boolean

    /**
     * Send message to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    fun sendMsg(isGroup: Boolean, targetId: String, message: String?) {
        if (message == null) return
        if (isGroup) sendGroupMsg(targetId, message)
        else sendUserMsg(targetId, message)
    }

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupMsg(targetId: String, message: String)

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserMsg(targetId: String, message: String)

    /**
     * Send picture to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    fun sendPictureWithText(isGroup: Boolean, targetId: String, message: String?) {
        if (message == null) return
        if (isGroup) sendGroupMsg(targetId, message)
        else sendUserMsg(targetId, message)
    }

    /**
     * Send a picture to group
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupPicWithText(targetId: String, message: String)

    /**
     * Send a picture to user
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserPicWithText(targetId: String, message: String)
}