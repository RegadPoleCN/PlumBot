package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.database.IDatabase
import org.spongepowered.configurate.ConfigurationNode
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.HostSQL
import taboolib.module.database.Table
import java.util.*
import javax.sql.DataSource

class MySQL(node: ConfigurationNode): IDatabase {

    private val host = HostSQL(DatabaseSource(node))
    private val dataSource by lazy { host.createDataSource() }

    private val table = Table("binding", host) {
        add { id() }
        add("user_id") {
            type(ColumnTypeSQL.BIGINT)
        }
        add("player_name") {
            type(ColumnTypeSQL.VARCHAR, 64)
        }
        add("player_uuid") {
            type(ColumnTypeSQL.TEXT)
        }
        add("binding_time") {
            type(ColumnTypeSQL.TEXT)
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

    override fun removeBindByNum(user: Long, id: Int): String? {
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