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
 * 群消息事件的不可变载荷，经 [me.regadpole.plumbot.api.PlumBotAPI.subscribeGroupMessage] 投递。
 * 现有字段语义稳定；后续小版本只追加字段，不改变既有字段含义。
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
 * 群成员减少事件（退群/被移出）的不可变载荷，
 * 经 [me.regadpole.plumbot.api.PlumBotAPI.subscribeGroupMemberDecrease] 投递。
 */
@PublicApi
data class GroupMemberDecreaseEvent(
    val botId: String,
    val groupId: Long,
    val userId: Long,
    val timestamp: Long,
)
