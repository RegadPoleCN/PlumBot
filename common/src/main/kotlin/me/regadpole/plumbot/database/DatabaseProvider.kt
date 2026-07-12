package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.IDatabase
import java.util.UUID

object DatabaseProvider {

    private var database: IDatabase? = null
    private var hasLoaded = false

    private val loadedDatabase: IDatabase
        get() = database!!

    fun start(database: IDatabase) {
        database.initialize()
        this.database = database
        hasLoaded = true
    }

    fun shutdown() {
        database?.let {
            it.close()
            database = null
            hasLoaded = false
        }
    }

    /**
     * Get the database instance
     * @see me.regadpole.plumbot.api.database.IDatabase
     * @return the object of IDatabase
     */
    fun getDatabase(): IDatabase? {
        return database
    }

    //
    fun getBindByUser(user: String): MutableMap<String, Int> {
        val map: MutableMap<String, Int> = LinkedHashMap()
        val info = loadedDatabase.getByUser(user)
        info.forEach {
            (map as java.util.LinkedHashMap<String, Int>)[it.playerName] = it.id
        }
        return map
    }
    fun getBindByName(name: String): String? {
        return loadedDatabase.getByName(name)?.userId
    }
    fun getBindById(id: Int): String? {
        return loadedDatabase.getById(id)?.userId
    }

    fun getUUIDByName(name: String): UUID? {
        return loadedDatabase.getByName(name)?.playerUUID?.let { UUID.fromString(it) }
    }
    fun getUUIDByUser(user: String): UUID? {
        return loadedDatabase.getByUser(user).firstOrNull()?.playerUUID?.let { UUID.fromString(it) }
    }

}