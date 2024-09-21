package me.regadpole.plumbot.internal

import me.regadpole.plumbot.PlumBot


object WhitelistHelper {
    fun checkCount(user: String): Boolean {
        val idList = PlumBot.getDatabase().getBind(user).values
        val maxCount: Int = PlumBot.getConfig().getConfig().groups.forwarding.whitelist.maxCount
        if (idList.isEmpty()) return true
        return idList.size < maxCount
    }

    fun checkIDNotExist(name: String): Boolean {
        return PlumBot.getDatabase().getBindByName(name).isNullOrEmpty()
    }

    fun checkIDBelong(user: String, name: String): Boolean {
        return PlumBot.getDatabase().getBindByName(name) == user
    }

    fun addAndGet(user: String, name: String): MutableMap<Int, String> {
        PlumBot.getDatabase().addBind(user, name)
        return PlumBot.getDatabase().getBind(user)
    }

    fun removeAndGet(user: String, id: Int): MutableMap<Int, String> {
        PlumBot.getDatabase().removeBind(user, id)
        return PlumBot.getDatabase().getBind(user)
    }
}