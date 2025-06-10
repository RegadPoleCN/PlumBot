package me.regadpole.plumbot.listener

interface BotHandler {
    fun onGroupMessage(message: String, groupId: Long, userId: Long)

    fun onWhitelistApply(message: String, groupId: Long, userId: Long)

    fun onWhitelistQuery(message: String, groupId: Long, userId: Long)

    fun onWhitelistRemove(message: String, groupId: Long, userId: Long)

    fun onPlayerList(message: String, groupId: Long, userId: Long)

    fun onUserDecrease(groupId: Long, userId: Long)
}