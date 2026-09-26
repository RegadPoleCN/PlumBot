package me.regadpole.plumbot.api.platform

import me.regadpole.plumbot.api.PublicApi

@PublicApi
interface PlatformConfig {
    fun getString(vararg path: String?): String?
    fun getLong(vararg path: String?): Long
    fun getInteger(vararg path: String?): Int
    fun getBoolean(vararg path: String?): Boolean
    fun getLongList(vararg path: String?): List<Long>
    fun getStringList(vararg path: String?): List<String?>
    fun getSubConfig(vararg path: String?): PlatformConfig
}
