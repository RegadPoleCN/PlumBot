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