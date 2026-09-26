package me.regadpole.plumbot.api.database

import me.regadpole.plumbot.api.PublicApi
import java.sql.SQLException
import java.util.UUID
import javax.sql.DataSource

@PublicApi
interface IDatabase {
    @Throws(ClassNotFoundException::class)
    fun initialize()

    @Throws(SQLException::class)
    fun close()

    @Throws(SQLException::class)
    fun getConnection(): DataSource

    fun getByUser(user: String): List<Binding>
    fun getByName(name: String): Binding?
    fun getById(id: Int): Binding?

    fun addBind(user: Long, name: String)

    fun removeBindByNum(user: Long, id: Int): String?
    fun removeBind(user: Long)
    fun removeBind(name: String)

    fun setUUID(name: String, uuid: UUID)
}
