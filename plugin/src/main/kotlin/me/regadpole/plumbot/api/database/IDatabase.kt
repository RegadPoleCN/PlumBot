package me.regadpole.plumbot.api.database

import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource


interface IDatabase: Cloneable {
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

    fun addBind(user: Long, name: String)

    fun removeBindByNum(user: Long, id: Int): String?
    fun removeBind(user: Long)
    fun removeBind(name: String)

    fun getBind(user: Long): MutableMap<String, Int>?
    fun getBind(name: String): Long?
    fun getBind(user: Long, id: Int): String?

    fun setUUID(name: String, uuid: UUID)
    fun getUUID(name: String): UUID?
    fun getUUID(user: Long): UUID?

    public override fun clone(): IDatabase {
        return super.clone() as IDatabase
    }
}