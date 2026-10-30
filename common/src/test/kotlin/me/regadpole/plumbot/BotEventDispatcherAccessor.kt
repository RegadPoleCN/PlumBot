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
