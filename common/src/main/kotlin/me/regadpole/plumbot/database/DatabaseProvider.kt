// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.api.database.IDatabase
import java.util.*

object DatabaseProvider {

    // @Volatile：查询线程（Bot 消息协程）与 start()/shutdown() 所在的加载线程不同，
    // 不加 volatile 会读到过期引用或半初始化状态。
    @Volatile
    private var database: IDatabase? = null
    @Volatile
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

    /**
     * 批量取"玩家名 → 绑定 QQ"：键为传入名字**原样**，未绑定值为 null。
     * 单次往返替代逐名查询（在线列表此前是 N+1）；匹配与 [getBindByName] 一致（trim + lowercase）。
     */
    fun getBindUserIdsByNames(names: Collection<String>): Map<String, String?> {
        val distinct = names.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val byName = loadedDatabase.getBindsByNames(distinct).associate { it.playerName to it.userId }
        return distinct.associateWith { name -> byName[name.trim().lowercase()] }
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
