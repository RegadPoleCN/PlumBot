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
import java.util.*

object DatabaseProvider {

    private var database: IDatabase? = null
    private var hasLoaded = false

    val isReady: Boolean
        get() = hasLoaded && database != null

    private val loadedDatabase: IDatabase
        get() = database ?: throw IllegalStateException("DatabaseProvider 尚未就绪，数据库未初始化完成！")

    fun start(database: IDatabase) {
        database.initialize()
        this.database = database
        hasLoaded = true
    }

    fun shutdown() {
        database?.let {
            it.close()
            database = null
            hasLoaded = false
        }
    }

    /**
     * Get the database instance
     * @see me.regadpole.plumbot.database.IDatabase
     * @return the object of IDatabase
     */
    fun getDatabase(): IDatabase? {
        return database
    }

    //
    fun getBindByUser(user: String): MutableMap<String, Int> {
        val map: MutableMap<String, Int> = LinkedHashMap()
        val info = loadedDatabase.getByUser(user)
        info.forEach {
            (map as LinkedHashMap<String, Int>)[it.playerName] = it.id
        }
        return map
    }

    fun getBindByUser(user: Long): MutableMap<String, Int> = getBindByUser(user.toString())
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