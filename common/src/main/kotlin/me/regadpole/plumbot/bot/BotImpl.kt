package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.bot.IBot

interface BotImpl: IBot {

    /**
     * Send message to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
        if (message.isNullOrEmpty()) return
        if (isGroup) {
            if (isPic) sendGroupPicWithText(targetId, message)
            else sendGroupMsg(targetId, message.replace(Regex("&([0-9a-fklmnor])"), "").replace(Regex("§([0-9a-fklmnor])"), ""))
        }
        else {
            if (isPic) sendUserPicWithText(targetId, message)
            else sendUserMsg(targetId, message.replace(Regex("&([0-9a-fklmnor])"), "").replace(Regex("§([0-9a-fklmnor])"), ""))
        }
    }

    /**
     * Send picture to the group/user
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     */
    override fun sendPictureWithText(isGroup: Boolean, targetId: Long, message: String?) {
        if (message.isNullOrEmpty()) return
        if (isGroup) sendGroupPicWithText(targetId, message)
        else sendUserPicWithText(targetId, message)
    }

}