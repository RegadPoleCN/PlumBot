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

    fun addBind(user: String, name: String)

    fun removeBind(user: String, id: Int)
    fun removeBind(user: String)
    fun removeBindByName(name: String)

    fun getBind(user: String): MutableMap<Int, String>
    fun getBindByName(name: String): String?
}