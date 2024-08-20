package me.regadpole.plumbot.database

import me.regadpole.plumbot.PlumBot
import taboolib.common.platform.function.info
import taboolib.common.util.asList
import taboolib.module.database.ColumnOptionSQL
import taboolib.module.database.ColumnTypeSQL
import taboolib.module.database.Table
import javax.sql.DataSource

class MySQL: Database {

    private val host = PlumBot.getConfig().getConfig().database.host
    private val dataSource by lazy { host.createDataSource() }

    private val table = Table("whitelist", host) {
        add { id() }
        add("user") {
            type(ColumnTypeSQL.VARCHAR, 64)
        }
        add("name") {
            type(ColumnTypeSQL.VARCHAR, 64) {
                options(ColumnOptionSQL.KEY)
            }
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

    override fun addBind(user: String, name: String) {
        table.insert(dataSource, "user", "name") {
            value(user, name)
        }
    }

    override fun removeBind(user: String, name: String) {
        table.delete(dataSource) {
            where { "user" eq user and ("name" eq name) }
        }
    }

    override fun removeBind(user: String) {
        table.delete(dataSource) {
            where { "user" eq user }
        }
    }

    override fun removeBindByName(name: String) {
        table.delete(dataSource) {
            where { "name" eq name }
        }
    }

    override fun getBind(user: String): List<String> {
        return table.select(dataSource) {
            rows("name")
            where("user" eq user)
        }.asList()
    }

    override fun getBindByName(name: String): String? {
        return table.select(dataSource) {
            rows("user")
            where("name" eq name)
        }.firstOrNull {
            getString("user")
        }
    }
}