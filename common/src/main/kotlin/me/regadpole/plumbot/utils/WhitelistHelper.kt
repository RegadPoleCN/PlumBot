package me.regadpole.plumbot.utils

import me.regadpole.plumbot.PlumBot
import me.regadpole.plumbot.database.DatabaseProvider

class WhitelistHelper private constructor(private val plugin: PlumBot) {
    companion object {
        @Volatile
        private var instance: WhitelistHelper? = null
        fun getInstance(plugin: PlumBot) =
            instance ?: synchronized(this) {
                instance ?: WhitelistHelper(plugin).also { instance = it }
            }
    }
    fun checkPlayerExists(player: String): Boolean {
        return DatabaseProvider.getBindByName(player) != null
    }

    fun checkUserBindingFull(userId: String): Boolean {
        val wl = DatabaseProvider.getBindByUser(userId)
        if (wl.isEmpty()) return false
        return DatabaseProvider.getBindByUser(userId).size >= plugin.config.getIntegerFromConfig("feature", "bind", "maxNum")
    }

    fun checkPlayerBelongToUser(player: String, userId: String): Boolean {
        return DatabaseProvider.getBindByName(player).equals(userId)
    }
}