package me.regadpole.plumbot

import me.regadpole.plumbot.task.TaskProviderImpl
import java.io.File
import java.util.Date
import kotlin.io.path.pathString

class DebugProvider(private val plugin: PlumBot) {
    private val loggerList = mutableListOf<String>()

    private lateinit var loggerFile: File

    private var initialized = false

    fun load() {
        TaskProviderImpl.submitAsync {
            if (plugin.config.getBoolean("debug", "enable")) {
                loggerFile = File(
                    plugin.config.getString("debug.file")?.replace("%plugin_folder%", plugin.dataDirectory.pathString) ?: (plugin.dataDirectory.pathString + "debug.log")
                )
                if (plugin.config.getLong("debug.save_interval") >= 1L) {
                    plugin.submitTimerAsync(0L, plugin.config.getLong("debug.save_interval") * 20L) {
                        if (loggerList.isNotEmpty()) {
                            loggerFile.appendText(loggerList.joinToString(""))
                        }
                    }
                }
                initialized = true
            }
        }
    }

    fun unload() {
        if (plugin.config.getBoolean("debug", "enable")) {
            if (plugin.config.getLong("debug.save_interval") != 0L) {
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
        TaskProviderImpl.submitAsync {
            if (!plugin.config.getBoolean("debug.enable")) return@submitAsync
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            if (plugin.config.getLong("debug.save_interval") == 0L) {
                loggerFile.appendText("[$time] $message\n")
            } else {
                loggerList.add("[$time] $message\n")
            }
        }
    }
}