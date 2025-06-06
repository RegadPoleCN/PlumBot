package me.regadpole.plumbot

import java.io.File
import java.util.Date
import kotlin.io.path.pathString

class DebugProvider(private val plugin: PlumBot) {
    private val loggerList = mutableListOf<String>()

    private lateinit var loggerFile: File

    private var initialized = false

    fun load() {
        if (plugin.config.getBooleanFromConfig("debug", "enable")) {
            loggerFile = File(
                plugin.config.getStringFromConfig("debug.file")?.replace("%plugin_folder%", plugin.dataDirectory.pathString) ?: (plugin.dataDirectory.pathString + "debug.log")
            )
            if (plugin.config.getLongFromConfig("debug.save_interval") >= 1L) {
                plugin.submitTimerAsync(0L, plugin.config.getLongFromConfig("debug.save_interval") * 20L) {
                    if (loggerList.isNotEmpty()) {
                        loggerFile.appendText(loggerList.joinToString(""))
                    }
                }
            }
            initialized = true
        }
    }

    fun unload() {
        if (plugin.config.getBooleanFromConfig("debug", "enable")) {
            if (plugin.config.getLongFromConfig("debug.save_interval") != 0L) {
                if (loggerList.isNotEmpty()) {
                    loggerFile.appendText(loggerList.joinToString(""))
                }
            }
            initialized = false
        }
    }

    fun reload() {
        if (initialized) {
            unload()
        }
        load()
    }

    fun log(message: String) {
        if (!plugin.config.getBooleanFromConfig("debug.enable")) return
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        if (plugin.config.getLongFromConfig("debug.save_interval") == 0L) {
            loggerFile.appendText("[$time] $message\n")
        } else {
            loggerList.add("[$time] $message\n")
        }
    }
}