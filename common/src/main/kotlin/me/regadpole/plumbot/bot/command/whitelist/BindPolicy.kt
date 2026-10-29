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

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.bot.command.BotCommandService

/**
 * 绑定写入的**唯一决策点**。
 *
 * 三条写入路径（管理员自助、管理员代绑、玩家自助）必须共用同一套"名额判断 + 重名判断 + 插入"：
 * 各自复制一份会在路径间产生行为差异，并发下还会绕过重名判断直接撞唯一索引。
 *
 * 顺序为：名额判断（可被管理员绕过）→ 幂等写入（内部先查后插，并由唯一索引兜底）。
 */
class BindPolicy(private val service: BotCommandService) {

    sealed interface Result {
        /** 写入成功。 */
        data object Added : Result

        /** 该角色名已被绑定（含并发写入冲突：此时同样视为"已存在"，不重复占用名额）。 */
        data object PlayerAlreadyBound : Result

        /** 该 QQ 的绑定名额已达上限。 */
        data object LimitReached : Result
    }

    /**
     * 尝试为 [userId] 写入角色名 [playerName] 的绑定。
     *
     * @param bypassLimit 是否跳过名额上限（对应 `feature.bind.adminBypass`，仅管理员路径可置 true）
     * @param sourceGroup 写入发生在哪个 QQ 群：群内路径传群号，游戏内指令与外部 API 传 `null`；
     *   只有带来源群的绑定会在"该群退群"时被自动解绑
     */
    fun bind(
        database: IDatabase,
        userId: Long,
        playerName: String,
        bypassLimit: Boolean,
        sourceGroup: String? = null,
    ): Result {
        // **名额校验与写入在同一事务内完成**：此前"先 count 再 insert"是两次独立往返，
        // 并发时两个请求都可能读到"还差一个名额"，从而突破上限。
        val limit = if (bypassLimit) 0 else service.bindingLimit()
        if (database.addBindIfAbsentWithinLimit(userId.toString(), playerName, limit, sourceGroup)) return Result.Added

        // 未写入：区分"名额已满"与"该角色名已被绑定"，让调用方能给出不同提示
        return if (limit > 0 && database.countByUser(userId.toString()) >= limit) {
            Result.LimitReached
        } else {
            Result.PlayerAlreadyBound
        }
    }
}
