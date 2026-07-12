package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.IDatabase
import taboolib.module.database.ColumnBuilder
import taboolib.module.database.Host
import taboolib.module.database.Table
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
        dataSource.connection.close()
    }

    override fun getConnection(): DataSource {
        return dataSource
    }

    override fun getByUser(user: String): List<Binding> {
        return Binding.of(table.select(dataSource) {
            where { "user_id" eq user }
        })
    }

    override fun getByName(name: String): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "player_name" eq name }
        }.firstOrNull { this })
    }

    override fun getById(id: Int): Binding? {
        return Binding.of(table.select(dataSource) {
            where { "id" eq id }
        }.firstOrNull { this })
    }

    override fun addBind(user: Long, name: String) {
        table.insert(dataSource, "user_id", "player_name") {
            value(user, name)
        }
    }

    override fun removeBindByNum(user: Long, id: Int): String? {
        val arg = table.select(dataSource) {
            where { "user_id" eq user and ("id" eq id) }
        }.firstOrNull { getString("player_name") }
        table.delete(dataSource) {
            where { "user_id" eq user and ("id" eq id) }
        }
        return arg
    }

    override fun removeBind(user: Long) {
        table.delete(dataSource) {
            where { "user_id" eq user }
        }
    }

    override fun removeBind(name: String) {
        table.delete(dataSource) {
            where { "player_name" eq name }
        }
    }

    override fun setUUID(name: String, uuid: UUID) {
        table.update(dataSource) {
            set("player_uuid", uuid.toString())
            where { "player_name" eq name }
        }
    }
}
