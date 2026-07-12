package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.BotCapability
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.platform.PlatformContext

class BotCommandService(val context: PlatformContext, val bot: IBot) {
    val config = context.config

    fun isFeatureEnabled(feature: String): Boolean = config.getBoolean("feature", feature, "enable")

    fun isAdmin(userId: Long): Boolean = config.getLongList("admins").contains(userId)

    fun bindPicEnabled(): Boolean = config.getBoolean("feature", "bind", "pic")

    fun listPicEnabled(): Boolean = config.getBoolean("feature", "list", "pic")

    fun render(template: String, vararg replacements: Pair<String, String>): String =
        replacements.fold(template) { result, (placeholder, value) -> result.replace(placeholder, value) }

    fun sendBindMessage(groupId: Long, message: String) {
        val pic = bindPicEnabled()
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
    }

    fun sendBindTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        sendBindMessage(groupId, render(template, *replacements))
    }

    fun sendListTemplate(groupId: Long, template: String, vararg replacements: Pair<String, String>) {
        val pic = listPicEnabled()
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, render(template, *replacements), pic)
    }

    fun sendWrongUsage(groupId: Long) {
        sendBindMessage(groupId, Messages.wrongUsage)
    }

    fun groupMemberNameCard(groupId: Long, userId: Long): Pair<String, String> {
        bot.requireCapability(BotCapability.GROUP_MEMBER_QUERY)
        return bot.getGroupUserName(groupId, userId) to bot.getGroupUserCard(groupId, userId)
    }

    fun userReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (name, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%user_id%" to userId.toString(),
            "%user_name%" to name,
            "%user_nick%" to card
        )
    }

    fun originReplacements(groupId: Long, userId: Long): Array<Pair<String, String>> {
        val (_, card) = groupMemberNameCard(groupId, userId)
        return arrayOf(
            "%origin_id%" to userId.toString(),
            "%origin_name%" to card
        )
    }

    fun checkPlayerExists(player: String): Boolean = DatabaseProvider.getBindByName(player) != null

    fun checkUserBindingFull(userId: String): Boolean {
        val wl = DatabaseProvider.getBindByUser(userId)
        if (wl.isEmpty()) return false
        return wl.size >= config.getInteger("feature", "bind", "maxNum")
    }

    fun checkPlayerBelongToUser(player: String, userId: String): Boolean =
        DatabaseProvider.getBindByName(player).equals(userId)
}
