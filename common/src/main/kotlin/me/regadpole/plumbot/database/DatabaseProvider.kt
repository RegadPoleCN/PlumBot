package me.regadpole.plumbot.database

import me.regadpole.plumbot.api.database.IDatabase
import java.util.UUID

object DatabaseProvider {

    private var database: IDatabase? = null
    private var hasLoaded = false

    fun start(database: IDatabase) {
        database.initialize()
        this.database = database
        hasLoaded = true
    }

    fun shutdown() {
        if (database != null) {
            this.database!!.close()
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
        val info = database!!.getByUser(user)
        info.forEach {
            (map as java.util.LinkedHashMap<String, Int>)[it.playerName] = it.id
        }
        return map
    }
    fun getBindByName(name: String): String? {
        return database!!.getByName(name)?.userId
    }
    fun getBindById(id: Int): String? {
        return database!!.getById(id)?.userId
    }

    fun getUUIDByName(name: String): UUID? {
        return database!!.getByName(name)?.playerUUID?.let { UUID.fromString(it) }
    }
    fun getUUIDByUser(user: String): UUID? {
        return database!!.getByUser(user).firstOrNull()?.playerUUID?.let { UUID.fromString(it) }
    }

}