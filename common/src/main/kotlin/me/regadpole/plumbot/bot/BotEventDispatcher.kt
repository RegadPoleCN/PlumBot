package me.regadpole.plumbot.bot

object BotEventDispatcher {
    fun dispatchGroupMessage(message: String, groupId: Long, senderId: Long) {
        BotProvider.getBot()?.handler?.onGroupMessage(message, groupId, senderId)
    }

    fun dispatchUserDecrease(groupId: Long, userId: Long) {
        BotProvider.getBot()?.handler?.onUserDecrease(groupId, userId)
    }
}
