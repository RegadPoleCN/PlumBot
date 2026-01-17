package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.utils.info
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.HostSQL
import taboolib.module.database.Table
import java.util.*
import javax.sql.DataSource

class MySQL: IDatabase {

    private val host = HostSQL(DatabaseSource(PlumBot.INSTANCE.config!!.getNode("database", "mysql")))
    private val dataSource by lazy { host.createDataSource() }

    private val table = Table("binding", host) {
        add { id() }
        add("user") {
            type(ColumnTypeSQL.BIGINT)
        }
        add("name") {
            type(ColumnTypeSQL.VARCHAR, 64)
        }
        add("uuid") {
            type(ColumnTypeSQL.TEXT)
        }
    }

    /**
     * initialize the database
     */
    override fun initialize() {
        table.createTable(dataSource)
        info("MySQL database initialized")
    }

    /**
     * close the database
     */
    override fun close() {
        dataSource.connection.close()
        info("MySQL database closed")
    }

    /**
     * get the database connection
     * @return the DataSource
     */
    override fun getConnection(): DataSource {
        return dataSource
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

    override fun getBind(user: Long): MutableMap<String, Int>? {
        var map: MutableMap<String, Int>? = null
        val result = table.select(dataSource) {
            rows("id", "name")
            where("user" eq user)
        }.forEach {
            if (map == null) map = LinkedHashMap()
            getString("name")
            if (wasNull()) return@forEach
            (map as LinkedHashMap<String, Int>)[getString("name")] = getInt("id")
        }
        return map
    }

    override fun getBind(name: String): Long? {
        return table.select(dataSource) {
            rows("user")
            where("name" eq name)
        }.firstOrNull {
            getLong("user")
        }
    }

    override fun getBind(user: Long, id: Int): String? {
        return table.select(dataSource) {
            where { "user" eq user and ("id" eq id) }
        }.firstOrNull { getString("name") }
    }

    override fun setUUID(name: String, uuid: UUID) {
        table.update(dataSource) {
            set("uuid", uuid.toString())
            where { "name" eq name }
        }
    }

    override fun getUUID(name: String): UUID? {
        val uuidStr = table.select(dataSource) {
            rows("uuid")
            where { "name" eq name }
        }.firstOrNull {
            getString("uuid")
        }
        if (uuidStr.isNullOrEmpty()) return null
        return UUID.fromString(uuidStr)
    }

    override fun getUUID(user: Long): UUID? {
        val uuidStr = table.select(dataSource) {
            rows("uuid")
            where { "user" eq user }
        }.firstOrNull {
            getString("uuid")
        }
        if (uuidStr.isNullOrEmpty()) return null
        return UUID.fromString(uuidStr)
    }
}