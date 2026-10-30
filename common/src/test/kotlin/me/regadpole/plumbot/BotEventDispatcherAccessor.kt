// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot

import me.regadpole.plumbot.bot.BotEventDispatcher

/**
 * 供 `bot` 包之外使用的测试访问器：[BotEventDispatcher] 刻意是 `internal`，但它的注册/分发
 * 必须在不依赖 Bukkit 的前提下被单测覆盖；本文件与它同模块，因此能调用其包内可见的成员。
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
