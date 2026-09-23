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
