// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.filter

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.internal.ShutdownRegistry
import me.regadpole.plumbot.utils.HttpConstants
import me.regadpole.plumbot.api.platform.PlatformContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

class FilterThesaurusManager(private val context: PlatformContext) {

    private val gson = Gson()
    private fun log(level: LogLevel, message: String) = context.log(level, message)

    /** 词库重载的专用 IO 作用域（见 [reloadAsync]）。 */
    private val reloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private companion object {
        /** 单个云端源的响应体上限：词库 JSON 远小于此，超限即视为异常响应。 */
        const val MAX_SOURCE_BYTES = 8L * 1024 * 1024
    }

    init {
        // 兜底登记：管理器由各平台创建，但并非所有平台停用时都会调用 shutdown()。
        ShutdownRegistry.register(this) { shutdown() }
    }

    /**
     * 非阻塞重载：派发到插件自有的 IO 作用域。
     *
     * 不能用平台调度器包 `runBlocking`：词库要下载/解析多个远程文件（可能耗时数秒），会把
     * **平台调度线程整段占住**，期间该平台的所有调度任务都被阻塞。
     */
    fun reloadAsync() {
        reloadScope.launch {
            runCatching { reload() }
                .onFailure {
                    HealthCounters.increment(HealthCounters.Key.FILTER_RELOAD_FAILURES)
                    log(LogLevel.WARN, "[PlumBot-Filter] 词库重载失败：${it.message}")
                }
        }
    }

    /** 关闭重载作用域（插件停用时调用）。 */
    fun shutdown() {
        ShutdownRegistry.unregister(this)
        reloadScope.cancel()
    }

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

    /** 重载串行化：并发触发时只允许**最新一次**生效，旧结果直接丢弃。 */
    private val reloadMutex = Mutex()
    private val reloadGeneration = AtomicLong(0)

    suspend fun reload() {
        val generation = reloadGeneration.incrementAndGet()
        reloadMutex.withLock {
            // 拿到锁后再判定：若期间又有更新的重载排队，本次结果已经过期
            if (generation != reloadGeneration.get()) {
                log(LogLevel.INFO, "[PlumBot-Filter] 已有更新的词库重载在排队，放弃本次过期结果。")
                return
            }
            reloadInternal()
        }
    }

    private suspend fun reloadInternal() = withContext(Dispatchers.IO) {
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
        val cloudUrls = if (config.getBoolean("filter", "cloud", "enable")) {
            config.getStringList("filter", "cloud", "urls").filterNotNull().filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val cloudWords = if (cloudUrls.isNotEmpty()) fetchMultipleCloudSources(cloudUrls) else emptySet()

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

        // 空结果保护：云端源配置了却一个词都没取到（网络全挂/返回体损坏）时，
        // 不能把正在生效的词库换成 null —— 那等于**过滤静默关闭**，且没有任何提示。
        val newMatcher = if (effectiveWords.isNotEmpty()) AhoCorasickMatcher(effectiveWords) else null
        if (shouldKeepPreviousMatcher(
                previousExists = matcher != null,
                newWordCount = effectiveWords.size,
                configuredSourceCount = cloudUrls.size
            )
        ) {
            log(
                LogLevel.WARN,
                "[PlumBot-Filter] 本次重载未取到任何词（云端源 ${cloudUrls.size} 个），" +
                    "保留上一次的词库继续生效，避免过滤被静默关闭。"
            )
        } else {
            matcher = newMatcher
        }

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
            connection.setRequestProperty("User-Agent", HttpConstants.BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", HttpConstants.ACCEPT_JSON_OR_TEXT)

            if (connection.responseCode in 200..299) {
                val jsonText = readBounded(connection, MAX_SOURCE_BYTES)
                if (jsonText == null) {
                    // 超过上限：**不落盘、不采用**，直接降级读快照。不能像 `readText()` 那样全量读入内存
                    // —— 一个异常大的响应就能把堆吃光，而源地址虽由管理员配置，却仍属"外部输入"。
                    log(
                        LogLevel.WARN,
                        "[PlumBot] 云端源响应超过上限（${MAX_SOURCE_BYTES / 1024 / 1024}MB），将尝试读取本地缓存。($url)"
                    )
                    connection.disconnect()
                } else {
                    val words = parseWordsFromJson(jsonText)
                    // 只有解析出内容才覆盖快照：把坏响应（HTML 错误页 / 空体）写进缓存会让"离线降级"这条退路一起失效。
                    if (words.isEmpty()) {
                        log(LogLevel.WARN, "[PlumBot] 云端源响应解析为空，保留本地快照不覆盖 ($url)")
                        // **不能在这里 return**：那样快照虽保住却没人读，本次 reload 的词库会变成空集，
                        // 过滤静默失效。必须继续往下走"降级读历史快照"，与"非 200"分支保持同一语义。
                    } else {
                        ThesaurusCache.writeAtomically(cacheFile, jsonText)
                        return words
                    }
                }
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

    /**
     * **有界读取**响应体：声明长度超限直接拒绝（不读入内存），声明不可信时按实际读取量兜底。
     * 返回 null 表示超限或读取失败 —— 调用方应据此降级读快照，而不是采用半截内容。
     */
    private fun readBounded(connection: HttpURLConnection, maxBytes: Long): String? {
        return try {
            val declared = connection.contentLengthLong
            if (declared > maxBytes) return null

            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            connection.inputStream.use { input ->
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return null
                    buffer.write(chunk, 0, read)
                }
            }
            buffer.toString(Charsets.UTF_8.name())
        } catch (e: Exception) {
            log(LogLevel.WARN, "[PlumBot] 读取云端源响应失败：${e.message}")
            null
        }
    }

    private fun parseWordsFromJson(json: String): Set<String> {        return try {
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
        HealthCounters.increment(HealthCounters.Key.FILTER_HITS)

        return when (action) {
            FilterAction.BLOCK -> {
                if (logMatches) {
                    // 脱敏：只记命中词与长度，**不落原文**（log_matches 默认 true，落原文等于把用户消息写进日志）。
                    log(LogLevel.INFO, "[敏感词拦截] 命中词: $matchedWords | 原文长度: ${rawText.length}")
                }
                FilterProcessResult(isBlocked = true, matchedWords = matchedWords, sanitizedText = "")
            }
            FilterAction.REPLACE -> {
                val replacedText = currentMatcher.replace(rawText, replacement)
                if (logMatches) {
                    log(LogLevel.INFO, "[敏感词脱敏] 命中词: $matchedWords | 文本长度: ${rawText.length}")
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

/** 供聊天桥接服务获取敏感词过滤器的持有器。 */
object FilterManagerHolder {
    @Volatile
    var manager: FilterThesaurusManager? = null
}

/**
 * 词库快照的**原子落盘**：先写临时文件再 move 覆盖。
 *
 * 直接 `writeText` 覆盖时，进程写到一半退出或并发重载同时写同一文件都会留下半个 JSON；
 * 而这份快照正是"云端不可达时的降级数据源"，坏掉等于把离线退路一起毁掉。
 */
internal object ThesaurusCache {

    fun writeAtomically(file: File, text: String) {
        runCatching {
            val parent = file.parentFile ?: return
            parent.mkdirs()
            val temp = File.createTempFile("${file.name}.", ".tmp", parent)
            try {
                temp.writeText(text)
                runCatching {
                    Files.move(
                        temp.toPath(), file.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                    )
                }.onFailure {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                runCatching { if (temp.exists()) temp.delete() }
            }
        }
    }
}

/**
 * 本次重载"一个词都没拿到"时是否应保留旧词库。
 *
 * 条件很窄：**之前确实有词库**、**这次解析出 0 个词**、**配置了云端源**，即"有源可取却取不到"
 * （网络全挂、返回体损坏）。用户本来就没配源时属于主动清空，应照常置空。
 */
internal fun shouldKeepPreviousMatcher(
    previousExists: Boolean,
    newWordCount: Int,
    configuredSourceCount: Int,
): Boolean = previousExists && newWordCount == 0 && configuredSourceCount > 0
