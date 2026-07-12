package me.regadpole.plumbot.platform

interface PlatformPluginDependency {
    fun isPluginAvailable(name: String): Boolean
}
