// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
