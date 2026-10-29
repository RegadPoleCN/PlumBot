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

import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.api.database.IDatabase
import java.util.*

object DatabaseProvider {

    private var database: IDatabase? = null
    private var hasLoaded = false

    /** @deprecated 零调用成员：请改用 `getDatabase() != null` 判断；主版本内保留。 */
    @Deprecated("改用 getDatabaseOrNull() != null")
    val isReady: Boolean
        get() = hasLoaded && database != null

    private val loadedDatabase: IDatabase
        get() = database ?: throw IllegalStateException("DatabaseProvider 尚未就绪，数据库未初始化完成！")

    /**
     * 初始化并接管数据库实例。
     *
     * 顺序保证：**先 `initialize()` 成功，再替换引用**。失败时关闭刚创建的实例并原样抛出，
     * 既不会留下"引用已就绪但表没建好"的半初始化状态，也不会泄漏连接池。
     */
    fun start(database: IDatabase) {
        try {
            database.initialize()
        } catch (t: Throwable) {
            HealthCounters.increment(HealthCounters.Key.DB_INIT_FAILURES)
            runCatching { database.close() }
            throw t
        }
        val previous = this.database
        if (previous != null && previous !== database) {
            runCatching { previous.close() }
        }
        this.database = database
        hasLoaded = true
        // 重载路径：shutdown() 取消过查询作用域，这里重新拉起，否则登录校验会一直超时。
        BindingLookupService.ensureRunning()
    }

    fun shutdown() {
        // 登录查询协程与缓存都挂在数据库生命周期上：先停掉，避免关闭过程中还在发查询。
        BindingLookupService.shutdown()
        BindingLookupCache.clear()
        database?.let {
            it.close()
            database = null
            hasLoaded = false
        }
    }

    /** 当前数据库实例；尚未初始化时为 `null`。 */
    fun getDatabase(): IDatabase? {
        return database
    }

    fun getBindByUser(user: String): MutableMap<String, Int> {
        val map: MutableMap<String, Int> = LinkedHashMap()
        val info = loadedDatabase.getByUser(user)
        info.forEach {
            (map as LinkedHashMap<String, Int>)[it.playerName] = it.id
        }
        return map
    }

    fun getBindByUser(user: Long): MutableMap<String, Int> = getBindByUser(user.toString())

    /**
     * 返回完整绑定记录（含 `bindingTime` 与 `playerUUID`）。
     *
     * 便捷方法 [getBindByUser] 把记录压成 `Map<玩家名, 绑定ID>`，会丢掉绑定时间等字段 ——
     * 需要展示绑定时间的路径请使用本方法。
     */
    fun getBindingsByUser(user: String): List<Binding> = loadedDatabase.getByUser(user.trim())

    /** 返回单条完整绑定记录（含 `bindingTime`），未绑定返回 `null`。 */
    fun getBindingByName(name: String): Binding? = loadedDatabase.getByName(name)

    fun addBind(user: Long, name: String) = loadedDatabase.addBind(user, name)
    fun addBind(user: String, name: String) = loadedDatabase.addBind(user, name)
    fun removeBindByUser(user: Long) = loadedDatabase.removeBindByUser(user.toString())
    fun removeBindByUser(user: String) = loadedDatabase.removeBindByUser(user)
    fun removeBind(user: Long) = loadedDatabase.removeBindByUser(user.toString())
    fun removeBind(name: String) = loadedDatabase.removeBind(name)
    fun getBindByName(name: String): String? {
        return loadedDatabase.getByName(name)?.userId
    }
    fun getBindById(id: Int): String? {
        return loadedDatabase.getById(id)?.userId
    }

    fun getUUIDByName(name: String): UUID? {
        return loadedDatabase.getByName(name)?.playerUUID?.let { UUID.fromString(it) }
    }
    fun getUUIDByUser(user: String): UUID? {
        return loadedDatabase.getByUser(user).firstOrNull()?.playerUUID?.let { UUID.fromString(it) }
    }

}
