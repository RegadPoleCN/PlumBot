package me.regadpole.plumbot.database

import java.sql.SQLException
import javax.sql.DataSource


interface Database {
    /**
     * initialize the database
     */
    @Throws(ClassNotFoundException::class)
    fun initialize()

    /**
     * close the database
     */
    @Throws(SQLException::class)
    fun close()

    /**
     * get the database connection
     * @return the DataSource
     */
    @Throws(SQLException::class)
    fun getConnection(): DataSource

}