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

package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.api.database.Binding
import taboolib.module.database.ColumnBuilder
import taboolib.module.database.Host
import taboolib.module.database.Table
import com.zaxxer.hikari.HikariDataSource
import java.util.*
import javax.sql.DataSource

/** 绑定表名：两个后端共用同一张表结构。 */
internal const val BINDING_TABLE_NAME = "binding"

abstract class AbstractBindingDatabase<H : Host<E>, E : ColumnBuilder> protected constructor(
    protected val host: H,
    /**
     * 表名。
     *
     * **必须是构造参数，不能是抽象属性**：本类的 [table] 在**自身构造期**初始化，
     * 若表名取自子类覆写的属性，此时子类字段尚未赋值，读出的是 null，
     * 而下游 `Table(name, host)` 对 name 做非空校验 → 构造即抛 NPE。
     */
    private val tableName: String,
) : IDatabase {

    protected abstract fun E.configureIdColumn()
    protected abstract fun E.configureUserIdColumn()
    protected abstract fun E.configurePlayerNameColumn()
    protected abstract fun E.configureTextColumn()

    /** 由后端实现：`source_group` 列是否已存在（迁移前判断，必须可重复执行）。 */
    protected abstract fun sourceGroupColumnPresent(connection: java.sql.Connection): Boolean

    protected val dataSource by lazy { host.createDataSource() }

    protected val table = Table<H, E>(tableName, host) {
        add { configureIdColumn() }
        add("user_id") { configureUserIdColumn() }
        add("player_name") { configurePlayerNameColumn() }
        add("player_uuid") { configureTextColumn() }
        add("binding_time") { configureTextColumn() }
        add("source_group") { configureTextColumn() }
    }

    /** 释放只做一次：重复调用直接返回，避免二次借还连接。 */
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun initialize() {
        table.createTable(dataSource)
        ensureSourceGroupColumn()
        ensureUniquePlayerNameIndex()
    }

    /**
     * 给**既有表**补 `source_group` 列。
     *
     * `createTable` 对已存在的表不会加列，因此升级路径必须显式 `ALTER TABLE`；
     * 多实例同时启动时后到者会撞"duplicate column"，此时复查一遍即可判定为成功。
     */
    private fun ensureSourceGroupColumn() {
        runCatching {
            dataSource.connection.use { conn ->
                if (sourceGroupColumnPresent(conn)) return@use
                conn.createStatement().use { st ->
                    st.executeUpdate("ALTER TABLE $tableName ADD COLUMN source_group TEXT")
                }
            }
        }.onFailure { error ->
            val present = runCatching {
                dataSource.connection.use { conn -> sourceGroupColumnPresent(conn) }
            }.getOrDefault(false)
            if (!present) {
                System.err.println("[PlumBot-DB] $tableName 表缺少 source_group 列且自动迁移失败（${error.message}）")
            }
        }
    }

    /**
     * 建立 `player_name` 唯一索引 —— 防止并发重复绑定。
     *
     * 迁移安全：历史数据可能已经有重复角色名，此时**既不能静默删数据、也不该让插件起不来**，
     * 因此只告警并跳过建索引；运维清理干净后，下次启动会自动补建（幂等）。
     */
    private fun ensureUniquePlayerNameIndex() {
        val duplicates = findDuplicatePlayerNames()
        if (duplicates.isNotEmpty()) {
            val sample = duplicates.take(5).joinToString()
            val more = if (duplicates.size > 5) " …" else ""
            System.err.println(
                "[PlumBot-DB] $tableName 表存在重复玩家名（$sample$more，共 ${duplicates.size} 个），" +
                    "已跳过唯一索引创建以免误删数据；请先人工核对，下次启动会自动补建。"
            )
            return
        }
        runCatching { createUniquePlayerNameIndex() }
            .onFailure { System.err.println("[PlumBot-DB] 唯一索引创建失败（本轮跳过）：${it.message}") }
    }

    private fun findDuplicatePlayerNames(): List<String> = runCatching {
        dataSource.connection.use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery(
                    "SELECT player_name FROM $tableName GROUP BY player_name HAVING COUNT(*) > 1"
                ).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
        }
    }.getOrElse {
        System.err.println("[PlumBot-DB] 重复玩家名检测失败（按无重复继续）：${it.message}")
        emptyList()
    }

    /** 由后端实现：为 `player_name` 建唯一索引，必须可重复执行。 */
    protected open fun createUniquePlayerNameIndex() {
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val ds = dataSource
        if (ds is HikariDataSource) {
            ds.close()
        } else if (ds is AutoCloseable) {
            ds.close()
        } else {
            ds.connection.close()
        }
    }

    override fun getConnection(): DataSource {
        return dataSource
    }

    private fun normalize(name: String): String = name.trim().lowercase()

    override fun getByUser(user: String): List<Binding> {
        return Binding.of(table.select(dataSource) {
            where { "user_id" eq user.trim() }
        })
    }

    override fun getByName(name: String): Binding? {
        val cleanName = normalize(name)
        // 走列表映射器（与 getByUser 同一条路径）再取首条：单行 ResultSet 在列表迭代结束后已关闭，
        // 直接映射那一条会抛 `SQLException: ResultSet closed`（登录白名单正走这里）。
        return Binding.of(table.select(dataSource) {
            where { "player_name" eq cleanName }
        }).firstOrNull()
    }

    override fun getById(id: Int): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "id" eq id }
        }).firstOrNull()
    }

    override fun getByPlayerUuid(uuid: String): Binding? {
        val clean = uuid.trim().lowercase()
        if (clean.isEmpty()) return null
        return Binding.of(table.select(dataSource) {
            where { "player_uuid" eq clean }
        }).firstOrNull()
    }

    override fun addBind(user: String, name: String) = addBindWithSource(user, name, null)

    /**
     * 带上来源群的插入。`sourceGroup` 为空时与旧写入**完全一致**（该列为 NULL），
     * 因此游戏内指令与外部 API 两条路径的落库结果没有变化。
     */
    private fun addBindWithSource(user: String, name: String, sourceGroup: String?) {
        val cleanName = normalize(name)
        val cleanUser = user.trim()
        val now = System.currentTimeMillis().toString()
        val cleanGroup = sourceGroup?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanGroup == null) {
            table.insert(dataSource, "user_id", "player_name", "binding_time") {
                value(cleanUser, cleanName, now)
            }
        } else {
            table.insert(dataSource, "user_id", "player_name", "binding_time", "source_group") {
                value(cleanUser, cleanName, now, cleanGroup)
            }
        }
        // 写后失效：下一次登录校验必须看到这条新绑定，不能等 TTL 自然过期。
        BindingLookupCache.invalidate(cleanName)
    }

    /**
     * 幂等写入：先查后插，并发时由唯一索引兜底（冲突视为"已存在"，不重复占用名额）。
     *
     * 这里只把 `SQLException`（唯一约束冲突等数据库层错误）折叠为 false，
     * 其它异常照常抛出，避免把"数据库连不上"伪装成"已绑定"。
     */
    override fun addBindIfAbsent(user: String, name: String): Boolean =
        addBindIfAbsent(user, name, null)

    override fun addBindIfAbsent(user: String, name: String, sourceGroup: String?): Boolean {
        val cleanName = normalize(name)
        if (getByName(cleanName) != null) return false
        return try {
            addBindWithSource(user, cleanName, sourceGroup)
            true
        } catch (e: java.sql.SQLException) {
            System.err.println("[PlumBot-DB] 绑定写入冲突，视为已存在：$cleanName（${e.message}）")
            false
        }
    }

    /**
     * 事务化写入：**名额校验与插入在同一事务内**。
     *
     * 为什么必须同事务：`BindPolicy` 此前先 `countByUser` 再写入，是两次独立往返 ——
     * 并发时两个请求都读到"还差一个名额"，于是**突破上限**。同名冲突由唯一索引兜底，
     * 但名额上限没有任何兜底。
     *
     * 为什么用原生 SQL：taboolib 的表 DSL 只接受 `DataSource`，每次调用自建连接，
     * 无法与外部事务共用 `Connection`；因此这里显式取连接、关自动提交、自行提交/回滚。
     * 列名与时间格式（epoch 毫秒字符串）与 DSL 写入保持一致。
     */
    override fun addBindIfAbsentWithinLimit(user: String, name: String, maxPerUser: Int): Boolean =
        addBindIfAbsentWithinLimit(user, name, maxPerUser, null)

    override fun addBindIfAbsentWithinLimit(
        user: String,
        name: String,
        maxPerUser: Int,
        sourceGroup: String?,
    ): Boolean {
        val cleanUser = user.trim()
        val cleanName = normalize(name)
        val cleanGroup = sourceGroup?.trim()?.takeIf { it.isNotEmpty() }
        if (maxPerUser <= 0) return addBindIfAbsent(cleanUser, cleanName, cleanGroup)

        return try {
            dataSource.connection.use { conn ->
                val originalAutoCommit = conn.autoCommit
                conn.autoCommit = false
                try {
                    val current = conn.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE user_id = ?").use { ps ->
                        ps.setString(1, cleanUser)
                        ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                    }
                    if (current >= maxPerUser) {
                        conn.rollback()
                        return@use false
                    }

                    // 同一事务内写入来源群：列与占位符一起拼，避免再写一条几乎相同的语句
                    val columns = "user_id, player_name, binding_time" +
                        if (cleanGroup == null) "" else ", source_group"
                    val placeholders = if (cleanGroup == null) "?, ?, ?" else "?, ?, ?, ?"
                    conn.prepareStatement(
                        "INSERT INTO $tableName ($columns) VALUES ($placeholders)"
                    ).use { ps ->
                        ps.setString(1, cleanUser)
                        ps.setString(2, cleanName)
                        ps.setString(3, System.currentTimeMillis().toString())
                        if (cleanGroup != null) ps.setString(4, cleanGroup)
                        ps.executeUpdate()
                    }
                    conn.commit()
                    BindingLookupCache.invalidate(cleanName)
                    true
                } catch (t: Throwable) {
                    runCatching { conn.rollback() }
                    throw t
                } finally {
                    runCatching { conn.autoCommit = originalAutoCommit }
                }
            }
        } catch (e: java.sql.SQLException) {
            // 唯一索引冲突 / 数据库繁忙：一律视为"未由本次写入"，不伪装成成功
            System.err.println("[PlumBot-DB] 事务化绑定写入失败：$cleanName（${e.message}）")
            false
        }
    }

    /**
     * 在数据库端计数：此前"名额是否已满"要先把该 QQ 的**所有绑定行取回内存**再 `.size`，
     * 行数随玩家规模线性增长，而这些操作在指令热路径上。
     *
     * 用原生 SQL 是为了避免表 DSL 的"取回全部行"语义；只读、不含时间列，因此不存在格式差异问题。
     */
    override fun countByUser(user: String): Int {
        val cleanUser = user.trim()
        return try {
            dataSource.connection.use { conn ->
                conn.prepareStatement("SELECT COUNT(*) FROM $tableName WHERE user_id = ?").use { ps ->
                    ps.setString(1, cleanUser)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
                }
            }
        } catch (e: java.sql.SQLException) {
            System.err.println("[PlumBot-DB] 统计绑定数失败（$cleanUser）：${e.message}")
            // 失败时回落到"取回再计数"，宁可慢也不要凭空判定"未满"而放行超额写入
            getByUser(cleanUser).size
        }
    }

    override fun removeBindByNum(user: String, id: Int): String? {
        val cleanUser = user.trim()
        val arg = table.select(dataSource) {
            where { "user_id" eq cleanUser and ("id" eq id) }
        }.firstOrNull { getString("player_name") }
        table.delete(dataSource) {
            where { "user_id" eq cleanUser and ("id" eq id) }
        }
        if (arg != null) {
            BindingLookupCache.invalidate(arg)
            BindingLookupCache.invalidateUser(cleanUser)
        }
        return arg
    }

    override fun removeBindByUser(user: String) {
        val cleanUser = user.trim()
        table.delete(dataSource) {
            where { "user_id" eq cleanUser }
        }
        BindingLookupCache.invalidateUser(cleanUser)
    }

    override fun removeBind(name: String) {
        val cleanName = normalize(name)
        table.delete(dataSource) {
            where { "player_name" eq cleanName }
        }
        BindingLookupCache.invalidate(cleanName)
    }

    override fun setUUID(name: String, uuid: UUID) {
        val cleanName = normalize(name)
        table.update(dataSource) {
            set("player_uuid", uuid.toString())
            where { "player_name" eq cleanName }
        }
    }
}
