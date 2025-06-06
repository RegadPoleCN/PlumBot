package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.IDatabase
import taboolib.module.database.ColumnTypeSQLite
import taboolib.module.database.HostSQLite
import taboolib.module.database.Table
import java.io.File
import java.util.*
import javax.sql.DataSource

class SQLite(path: String): IDatabase {

    private val host = HostSQLite(File(path))
    private val dataSource by lazy { host.createDataSource() }

    private val table = Table("binding", host) {
        add { id() }
        add("user_id") {
            type(ColumnTypeSQLite.INTEGER)
        }
        add("player_name") {
            type(ColumnTypeSQLite.TEXT)
        }
        add("player_uuid") {
            type(ColumnTypeSQLite.TEXT)
        }
        add("binding_time") {
            type(ColumnTypeSQLite.TEXT)
        }
    }

    /**
     * initialize the database
     */
    override fun initialize() {
        table.createTable(dataSource)
    }

    /**
     * close the database
     */
    override fun close() {
        dataSource.connection.close()
    }

    /**
     * get the database connection
     * @return the DataSource
     */
    override fun getConnection(): DataSource {
        return dataSource
    }

    override fun getByUser(user: String): List<Binding> {
        return Binding.of(table.select(dataSource) {
            where { "user" eq user }
        })
    }

    override fun getByName(name: String): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "name" eq name }
        }.firstOrNull { this })
    }

    override fun getById(id: Int): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "id" eq id }
        }.firstOrNull { this })
    }

    override fun addBind(user: Long, name: String) {
        table.insert(dataSource, "user", "name") {
            value(user, name)
        }
    }

    override fun removeBindByNum(user: Long, id: Int):String? {
        val arg = table.select(dataSource) {
            where { "user" eq user and ("id" eq id) }
        }.firstOrNull { getString("name") }
        table.delete(dataSource) {
            where { "user" eq user and ("id" eq id) }
        }
        return arg
    }

    override fun removeBind(user: Long) {
        table.delete(dataSource) {
            where { "user" eq user }
        }
    }

    override fun removeBind(name: String) {
        table.delete(dataSource) {
            where { "name" eq name }
        }
    }

    override fun setUUID(name: String, uuid: UUID) {
        table.update(dataSource) {
            set("uuid", uuid.toString())
            where { "name" eq name }
        }
    }
}