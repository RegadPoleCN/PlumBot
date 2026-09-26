package me.regadpole.plumbot

import me.regadpole.plumbot.platform.PlatformTaskHandle
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.pathString

class DebugProvider(private val plugin: PlumBot) {
    private val loggerList = ConcurrentLinkedQueue<String>()
    private val diskWriteLock = ReentrantLock()

    private lateinit var loggerFile: File

    @Volatile
    private var initialized = false

    @Volatile
    private var timerHandle: PlatformTaskHandle? = null

    fun load() {
        val config = plugin.config
        if (config.getBoolean("debug", "enable")) {
            val filePath = config.getString("debug", "file")
                ?.replace("%plugin_folder%", plugin.dataDirectory.pathString)
                ?: (plugin.dataDirectory.pathString + "/debug.log")
            loggerFile = File(filePath)
            loggerFile.parentFile?.mkdirs()

            val saveInterval = config.getLong("debug", "save_interval")
            if (saveInterval >= 1L) {
                // 绑定任务句柄，采用标准 Duration，避免时间单位混淆
                val handle = plugin.platform.scheduler.runRepeatingAsync(
                    kotlin.time.Duration.ZERO,
                    kotlin.time.Duration.parse("${saveInterval}s")
                ) {
                    flushLogsToDisk()
                }
                timerHandle = handle
            }
            initialized = true
        }
    }

    fun unload() {
        if (!initialized) return

        timerHandle?.cancel()
        timerHandle = null

        flushLogsToDisk()
        initialized = false
    }

    fun reload() {
        if (initialized) {
            unload()
        }
        load()
    }

    fun log(message: String) {
        if (!initialized || !::loggerFile.isInitialized) return
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val entry = "[$time] $message\n"

        if (plugin.config.getLong("debug", "save_interval") == 0L) {
            // 实时写入：互斥锁保护，防止多协程写坏文件或 Windows 报占用异常
            diskWriteLock.withLock {
                try {
                    loggerFile.appendText(entry)
                } catch (e: Exception) {
                    System.err.println("[PlumBot-Debug] 实时写入日志失败: ${e.message}")
                }
            }
        } else {
            loggerList.add(entry)
        }
    }

    private fun flushLogsToDisk() {
        if (!::loggerFile.isInitialized) return
        if (loggerList.isEmpty()) return

        diskWriteLock.withLock {
            val logs = generateSequence { loggerList.poll() }.toList()
            if (logs.isNotEmpty()) {
                try {
                    loggerFile.appendText(logs.joinToString(""))
                } catch (e: Exception) {
                    System.err.println("[PlumBot-Debug] 批量落盘日志失败: ${e.message}")
                }
            }
        }
    }
}
