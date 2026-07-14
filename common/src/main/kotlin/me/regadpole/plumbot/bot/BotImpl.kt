package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.bot.IBot

interface BotImpl: IBot {

    /**
     * Send message to the group/user.
     *
     * When [isPic] is `true`, this delegates to [sendGroupPicWithText]
     * (for [isGroup] = `true`) or [sendUserPicWithText] (for
     * [isGroup] = `false`); otherwise it strips Minecraft / ANSI color
     * codes via [stripColorCodes] and forwards to [sendGroupMsg] /
     * [sendUserMsg].
     *
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     * @param isPic when `true`, send as picture with text
     */
    override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
        if (message.isNullOrEmpty()) return
        if (isGroup) {
            if (isPic) sendGroupPicWithText(targetId, message)
            else sendGroupMsg(targetId, stripColorCodes(message))
        }
        else {
            if (isPic) sendUserPicWithText(targetId, message)
            else sendUserMsg(targetId, stripColorCodes(message))
        }
    }

    /**
     * Strip Minecraft (`&X`) and section-sign (`§X`) color/format codes
     * from [message]. Centralized so the regular expressions are only
     * defined once and reused by every text-message send path.
     */
    private fun stripColorCodes(message: String): String =
        message
            .replace(COLOR_CODE_AMP, "")
            .replace(COLOR_CODE_SECTION, "")

    private companion object {
        /** Matches `&` followed by a valid Minecraft color/format code. */
        private val COLOR_CODE_AMP = Regex("&([0-9a-fklmnor])")
        /** Matches `§` followed by a valid Minecraft color/format code. */
        private val COLOR_CODE_SECTION = Regex("§([0-9a-fklmnor])")
    }

}
