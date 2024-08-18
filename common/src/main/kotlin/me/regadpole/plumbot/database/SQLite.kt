package me.regadpole.plumbot.database

import me.regadpole.plumbot.PlumBot
import javax.sql.DataSource

class SQLite: Database {
    private val host = PlumBot.getConfig().getConfig().database.sqliteHost
    private val dataSource by lazy { host.createDataSource() }

    /**
     * initialize the database
     */
    override fun initialize() {
    }

    /**
     * close the database
     */
    override fun close() {
        TODO("Not yet implemented")
    }

    /**
     * get the database connection
     * @return the DataSource
     */
    override fun getConnection(): DataSource {
        return dataSource
    }
}