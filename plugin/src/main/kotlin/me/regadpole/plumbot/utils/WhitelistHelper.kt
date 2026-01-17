package me.regadpole.plumbot.utils

import me.regadpole.plumbot.PlumBot

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
        return plugin.database!!.getBind(player) != null
    }

    fun checkUserBindingFull(userId: Long): Boolean {
        val wl = plugin.database!!.getBind(userId)
        if (wl.isNullOrEmpty()) return false
        return plugin.database!!.getBind(userId)!!.size >= plugin.config!!.getIntegerFromConfig("feature", "bind", "maxNum")
    }

    fun checkPlayerBelongToUser(player: String, userId: Long): Boolean {
        return plugin.database!!.getBind(player) == userId
    }
}