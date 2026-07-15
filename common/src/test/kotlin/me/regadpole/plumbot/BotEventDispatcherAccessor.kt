package me.regadpole.plumbot

import me.regadpole.plumbot.bot.BotEventDispatcher

/**
 * Test-only accessor for [BotEventDispatcher] from outside the `bot` package.
 *
 * The dispatcher is intentionally `internal` (KDoc labelled "framework
 * internal") but its public-API registration / fan-out must be unit-tested
 * without depending on Bukkit. This file lives in the same module so it can
 * call the dispatcher's package-private surface.
 */
object BotEventDispatcherAccessor {
    fun dispatchGroupMessage(message: String, groupId: Long, userId: Long) {
        BotEventDispatcher.dispatchGroupMessage(message, groupId, userId)
    }

    fun dispatchUserDecrease(groupId: Long, userId: Long) {
        BotEventDispatcher.dispatchUserDecrease(groupId, userId)
    }

    fun clearAllListeners() {
        BotEventDispatcher.clearAllListeners()
    }
}
