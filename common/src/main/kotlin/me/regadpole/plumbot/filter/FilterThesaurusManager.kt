/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.filter

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class FilterThesaurusManager(private val context: PlatformContext) {

    private val gson = Gson()
    private fun log(level: LogLevel, message: String) = context.log(level, message)

    @Volatile
    private var matcher: AhoCorasickMatcher? = null

    @Volatile
    private var action: FilterAction = FilterAction.REPLACE

    @Volatile
    private var replacement: String = "***"

    @Volatile
    private var adminBypass: Boolean = true

    @Volatile
    private var logMatches: Boolean = true

    suspend fun reload() = withContext(Dispatchers.IO) {
        val config = context.config
        val enabled = config.getBoolean("filter", "enable")
        if (!enabled) {
            matcher = null
            return@withContext
        }

        action = FilterAction.fromString(config.getString("filter", "action"))
        replacement = config.getString("filter", "replacement") ?: "***"
        adminBypass = config.getBoolean("filter", "admin_bypass")
        logMatches = config.getBoolean("filter", "log_matches")

        // 1. 并发抓取多云端源词库列表
        val cloudWords = if (config.getBoolean("filter", "cloud", "enable")) {
            val urls = config.getStringList("filter", "cloud", "urls").filterNotNull().filter { it.isNotBlank() }
            fetchMultipleCloudSources(urls)
        } else {
            emptySet()
        }

        // 2. 本地自定义黑名单
        val localWords = config.getStringList("filter", "local_words").filterNotNull().filter { it.isNotBlank() }.toSet()

        // 3. 忽略/误杀白名单（小写归一化）
        val ignoredWords = config.getStringList("filter", "ignored_words").filterNotNull()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        // 4. 三元组计算：(Cloud + Local) - Ignored
        val effectiveWords = (cloudWords + localWords)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.lowercase() in ignoredWords }
            .toSet()

        matcher = if (effectiveWords.isNotEmpty()) AhoCorasickMatcher(effectiveWords) else null

        log(
            LogLevel.INFO,
            "[PlumBot] 敏感词库加载完成: 聚合云端 ${cloudWords.size} 词, 本地补充 ${localWords.size} 词, 排除白名单 ${ignoredWords.size} 词 -> 最终生效词数: ${effectiveWords.size}"
        )
    }

    private suspend fun fetchMultipleCloudSources(urls: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val timeoutMs = context.config.getInteger("filter", "cloud", "timeout_ms").let { if (it <= 0) 5000 else it }
        val rawCacheDir = context.config.getString("filter", "cloud", "cache_dir")
            ?: "%plugin_folder%/filter_cache"
        val cacheDir = File(rawCacheDir.replace("%plugin_folder%", context.dataDirectory.toString()))
        cacheDir.mkdirs()

        val tasks = urls.map { url ->
            async {
                fetchSingleSourceWithCache(url, cacheDir, timeoutMs)
            }
        }
        val results = tasks.awaitAll()
        results.flatten().toSet()
    }

    private fun fetchSingleSourceWithCache(url: String, cacheDir: File, timeoutMs: Int): Set<String> {
        val urlHash = sha256(url).take(16)
        val cacheFile = File(cacheDir, "source_$urlHash.json")

        // 优先联网抓取
        try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("User-Agent", "PlumBot/3.0.0 (Minecraft-QQ-Bridge)")

            if (connection.responseCode in 200..299) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                cacheFile.writeText(jsonText)
                return parseWordsFromJson(jsonText)
            } else {
                log(LogLevel.WARN, "[PlumBot] 云端源响应非 200 ($url, code=${connection.responseCode})，将尝试读取本地缓存。")
            }
        } catch (e: Exception) {
            log(LogLevel.WARN, "[PlumBot] 云端源拉取失败 ($url): ${e.message}，将尝试读取本地缓存。")
        }

        // 降级：读取该源的历史本地快照文件
        if (cacheFile.exists() && cacheFile.isFile) {
            return try {
                parseWordsFromJson(cacheFile.readText())
            } catch (e: Exception) {
                log(LogLevel.ERROR, "[PlumBot] 本地缓存快照损坏 (${cacheFile.name}): ${e.message}")
                emptySet()
            }
        }
        return emptySet()
    }

    private fun parseWordsFromJson(json: String): Set<String> {
        return try {
            val db = gson.fromJson(json, CloudDatabase::class.java)
            db?.words?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun process(rawText: String, isBypass: Boolean = false): FilterProcessResult {
        val currentMatcher = matcher
        if (currentMatcher == null || (isBypass && adminBypass)) {
            return FilterProcessResult(isBlocked = false, matchedWords = emptyList(), sanitizedText = rawText)
        }

        val matches = currentMatcher.match(rawText)
        if (matches.isEmpty()) {
            return FilterProcessResult(isBlocked = false, matchedWords = emptyList(), sanitizedText = rawText)
        }

        val matchedWords = matches.map { it.word }.distinct()

        return when (action) {
            FilterAction.BLOCK -> {
                if (logMatches) {
                    log(LogLevel.INFO, "[敏感词拦截] 消息触发违规词阻断: $matchedWords | 原文: $rawText")
                }
                FilterProcessResult(isBlocked = true, matchedWords = matchedWords, sanitizedText = "")
            }
            FilterAction.REPLACE -> {
                val replacedText = currentMatcher.replace(rawText, replacement)
                if (logMatches) {
                    log(LogLevel.INFO, "[敏感词脱敏] 替换词: $matchedWords -> $replacedText")
                }
                FilterProcessResult(isBlocked = false, matchedWords = matchedWords, sanitizedText = replacedText)
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * 内部单例持有器，供聊天桥接服务获取敏感词过滤器。
 */
object FilterManagerHolder {
    @Volatile
    var manager: FilterThesaurusManager? = null
}
