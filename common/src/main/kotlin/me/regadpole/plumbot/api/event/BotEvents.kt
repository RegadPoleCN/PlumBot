package me.regadpole.plumbot.api.event

import me.regadpole.plumbot.api.PublicApi

/**
 * Immutable payload representing a group message received by the bot.
 *
 * Third-party plugins receive instances of this class via
 * [me.regadpole.plumbot.PlumBotAPI.subscribeGroupMessages]. All fields are stable;
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
 * Immutable payload representing a user decrease (member leaves) event.
 *
 * Third-party plugins receive instances of this class via
 * [me.regadpole.plumbot.PlumBotAPI.subscribeUserDecrease].
 */
@PublicApi
data class UserDecreaseEvent(
    val botId: String,
    val groupId: Long,
    val userId: Long,
    val timestamp: Long,
)
