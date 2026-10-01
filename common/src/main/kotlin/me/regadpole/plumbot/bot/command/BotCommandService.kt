/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.bot.requireCapability
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.api.platform.PlatformContext

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

    fun sendRawMessage(groupId: Long, message: String, pic: Boolean = false) {
        bot.requireCapability(BotCapability.GROUP_MESSAGE_SEND)
        if (pic) bot.requireCapability(BotCapability.IMAGE_SEND)
        bot.sendMsg(true, groupId, message, pic)
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

    fun checkUserBindingFull(userId: String): Boolean =
        DatabaseProvider.getBindByUser(userId).size >= config.getInteger("feature", "bind", "maxNum")

    fun checkPlayerBelongToUser(player: String, userId: String): Boolean =
        DatabaseProvider.getBindByName(player).equals(userId)
}
