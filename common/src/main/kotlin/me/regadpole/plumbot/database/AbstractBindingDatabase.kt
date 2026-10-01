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

abstract class AbstractBindingDatabase<H : Host<E>, E : ColumnBuilder> protected constructor(
    protected val host: H
) : IDatabase {

    protected abstract val tableName: String
    protected abstract fun E.configureIdColumn()
    protected abstract fun E.configureUserIdColumn()
    protected abstract fun E.configurePlayerNameColumn()
    protected abstract fun E.configureTextColumn()

    protected val dataSource by lazy { host.createDataSource() }

    protected val table = Table<H, E>(tableName, host) {
        add { configureIdColumn() }
        add("user_id") { configureUserIdColumn() }
        add("player_name") { configurePlayerNameColumn() }
        add("player_uuid") { configureTextColumn() }
        add("binding_time") { configureTextColumn() }
    }

    override fun initialize() {
        table.createTable(dataSource)
    }

    override fun close() {
        val ds = dataSource
        if (ds is HikariDataSource) {
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
        return Binding.of(table.select(dataSource) {
            where { "player_name" eq cleanName }
        }.firstOrNull { this })
    }

    override fun getById(id: Int): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "id" eq id }
        }.firstOrNull { this })
    }

    override fun addBind(user: String, name: String) {
        val cleanName = normalize(name)
        val cleanUser = user.trim()
        val now = System.currentTimeMillis().toString()
        table.insert(dataSource, "user_id", "player_name", "binding_time") {
            value(cleanUser, cleanName, now)
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
        return arg
    }

    override fun removeBindByUser(user: String) {
        val cleanUser = user.trim()
        table.delete(dataSource) {
            where { "user_id" eq cleanUser }
        }
    }

    override fun removeBind(name: String) {
        val cleanName = normalize(name)
        table.delete(dataSource) {
            where { "player_name" eq cleanName }
        }
    }

    override fun setUUID(name: String, uuid: UUID) {
        val cleanName = normalize(name)
        table.update(dataSource) {
            set("player_uuid", uuid.toString())
            where { "player_name" eq cleanName }
        }
    }
}
