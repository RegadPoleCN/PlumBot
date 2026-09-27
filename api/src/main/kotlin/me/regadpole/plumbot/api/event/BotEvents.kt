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

package me.regadpole.plumbot.api.event

import me.regadpole.plumbot.api.PublicApi

/**
 * Immutable payload representing a group message received by the bot.
 *
 * Third-party plugins receive instances of this class via
 * [me.regadpole.plumbot.PlumBotAPI.subscribeGroupMessage]. All fields are stable;
 * additional fields MAY be added in future minor versions.
 */
@PublicApi
data class GroupMessageEvent(
    val botId: String,
    val groupId: Long,
    val userId: Long,
    val message: String,
    val timestamp: Long,
)

/**
 * Immutable payload representing a group member decrease event.
 *
 * Third-party plugins receive instances of this class via
 * [me.regadpole.plumbot.PlumBotAPI.subscribeGroupMemberDecrease].
 */
@PublicApi
data class GroupMemberDecreaseEvent(
    val botId: String,
    val groupId: Long,
    val userId: Long,
    val timestamp: Long,
)

/** Alias for compatibility before v3 release if any */
typealias UserDecreaseEvent = GroupMemberDecreaseEvent
