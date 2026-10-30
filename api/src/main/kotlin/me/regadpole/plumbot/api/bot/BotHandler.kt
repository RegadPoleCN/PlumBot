// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.PublicApi

@PublicApi
interface BotHandler {
    fun onGroupMessage(message: String, groupId: Long, userId: Long)

    fun onWhitelistApply(message: String, groupId: Long, userId: Long)

    fun onWhitelistQuery(message: String, groupId: Long, userId: Long)

    fun onWhitelistRemove(message: String, groupId: Long, userId: Long)

    fun onPlayerList(message: String, groupId: Long, userId: Long)

    fun onUserDecrease(groupId: Long, userId: Long)

    /**
     * 释放处理器持有的资源（协程作用域等）。默认无操作，第三方实现可忽略。
     * 由适配器在 [IBot.shutdown] 时调用；内置实现在此取消出图/状态查询作用域，
     * 否则 Bot 反复启停会持续泄漏线程。
     */
    fun shutdown() {
    }
}