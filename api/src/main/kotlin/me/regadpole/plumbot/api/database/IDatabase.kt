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

package me.regadpole.plumbot.api.database

import me.regadpole.plumbot.api.PublicApi
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

@PublicApi
interface IDatabase {
    @Throws(ClassNotFoundException::class)
    fun initialize()

    @Throws(SQLException::class)
    fun close()

    /**
     * 底层连接池本体。
     *
     * **仅供实现层内部事务与测试使用**：返回的是 `DataSource`（连接池），不是单条连接，
     * 调用方必须自行 `use {}` 借还；第三方扩展应优先使用本接口的查询/写入方法，
     * 直接持有连接会绕过规范化与唯一约束的封装。
     */
    @Throws(SQLException::class)
    fun getConnection(): DataSource

    fun getByUser(user: String): List<Binding>
    fun getByName(name: String): Binding?
    fun getById(id: Int): Binding?

    /**
     * 统计某 QQ 的绑定条数。
     *
     * 默认实现为"取回再计数"（兼容既有实现）；内置实现会走数据库端 `COUNT(*)`，
     * 避免在指令热路径上把该 QQ 的全部绑定行搬进内存（A04 前置优化）。
     */
    fun countByUser(user: String): Int = getByUser(user).size

    /**
     * 在**同一事务**内完成"名额校验 + 幂等写入"（A04）。
     *
     * 默认实现是两次独立调用（`countByUser` + `addBindIfAbsent`），**非原子**，仅为兼容外部实现；
     * 内置实现使用数据库事务：并发下同一 QQ 的名额上限**不可能被突破**。
     *
     * @param maxPerUser 名额上限；`<= 0` 表示不限（管理员绕过路径）
     * @return 是否**由本次调用写入**（角色名已被绑定、或名额已满时返回 false）
     */
    fun addBindIfAbsentWithinLimit(user: String, name: String, maxPerUser: Int): Boolean =
        (maxPerUser <= 0 || countByUser(user) < maxPerUser) && addBindIfAbsent(user, name)

    /**
     * 按玩家 UUID 查询绑定记录。
     *
     * 默认返回 null 以便旧实现继续编译；**只有记录过 UUID 的角色（至少进过一次服）
     * 才可能命中**，从未进服的绑定 `player_uuid` 为空。
     */
    fun getByPlayerUuid(uuid: String): Binding? = null

    fun addBind(user: String, name: String)

    /**
     * 幂等写入绑定：已存在同一角色时返回 false，不覆盖既有记录。
     *
     * 默认实现退化为直接插入（等价旧语义）；实现层应结合唯一约束保证并发安全。
     */
    fun addBindIfAbsent(user: String, name: String): Boolean {
        addBind(user, name)
        return true
    }
    fun removeBindByNum(user: String, id: Int): String?
    fun removeBindByUser(user: String)
    fun removeBind(name: String)

    fun setUUID(name: String, uuid: UUID)

    fun getByUser(user: Long): List<Binding> = getByUser(user.toString())
    fun addBind(user: Long, name: String) = addBind(user.toString(), name)
    fun removeBindByNum(user: Long, id: Int): String? = removeBindByNum(user.toString(), id)
    fun removeBindByUser(user: Long) = removeBindByUser(user.toString())
    fun removeBind(user: Long) = removeBindByUser(user.toString())
}
