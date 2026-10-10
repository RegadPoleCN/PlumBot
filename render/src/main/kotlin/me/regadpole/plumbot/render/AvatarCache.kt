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

package me.regadpole.plumbot.render

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64

/**
 * 头像磁盘缓存。
 *
 * **为什么必需**：头像原先由无头浏览器在渲染时逐张去取。一旦列表类 route 出图，
 * 一张卡片就要取 N 个玩家头像，全部落在渲染超时窗口内。服务端预取 + 磁盘缓存后，
 * 浏览器只收到成品 `data:` URL —— 渲染耗时与外部服务抖动被彻底解耦。
 */
internal class AvatarCache(
    private val dir: File,
    private val ttlMillis: Long,
    /** 磁盘缓存的文件数上限；超出后按最旧优先清理。 */
    private val maxFiles: Int = DEFAULT_MAX_FILES
) {

    /** 上次清理时间：写操作高频，清理做节流，避免每次都列目录。 */
    @Volatile
    private var lastPruneAt = 0L

    fun read(cacheKey: String): ByteArray? {
        val file = fileFor(cacheKey)
        if (!file.isFile) return null
        val age = System.currentTimeMillis() - file.lastModified()
        if (ttlMillis > 0 && age > ttlMillis) return null
        return runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    fun write(cacheKey: String, bytes: ByteArray) {
        runCatching {
            dir.mkdirs()
            val target = fileFor(cacheKey)
            // 临时名必须**每次唯一**：旧实现固定用 "<name>.tmp"，
            // 并发写同一个 key 时两个线程会往同一个文件里交错写，
            // ATOMIC_MOVE 会把半个文件换进正式位置（表现为损坏的 PNG）。
            val temp = File.createTempFile("${target.name}.", ".tmp", dir)
            try {
                temp.writeBytes(bytes)
                runCatching {
                    Files.move(
                        temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
                    )
                }.onFailure {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                runCatching { if (temp.exists()) temp.delete() }
            }
        }
        pruneIfDue()
    }

    /**
     * 清理过期条目并在超出 [maxFiles] 时按最旧优先删除。
     *
     * 背景：`ttlMillis` 过去**只让读取落空**，陈旧文件永远不会被删除，
     * 缓存目录随着每个新头像/皮肤地址无限增长（一次列表出图就能产生 N 个文件）。
     *
     * @return 本次删除的文件数
     */
    fun prune(now: Long = System.currentTimeMillis(), force: Boolean = false): Int {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") } ?: return 0
        var removed = 0
        val survivors = ArrayList<File>(files.size)
        for (file in files) {
            val expired = ttlMillis > 0 && now - file.lastModified() > ttlMillis
            if (expired || (force && file.lastModified() < lastPruneAt)) {
                if (file.delete()) removed++
            } else {
                survivors += file
            }
        }
        if (survivors.size > maxFiles) {
            survivors.sortBy { it.lastModified() }
            survivors.take(survivors.size - maxFiles).forEach { if (it.delete()) removed++ }
        }
        lastPruneAt = now
        return removed
    }

    private fun pruneIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAt < PRUNE_INTERVAL_MS) return
        runCatching { prune(now) }
    }

    private fun fileFor(cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$name.png")
    }

    private companion object {
        /** 磁盘缓存文件数上限（约 2048 × 单张头像，量级为几十 MB）。 */
        const val DEFAULT_MAX_FILES = 2048

        /** 清理节流间隔：写操作高频，不必每次都列目录。 */
        const val PRUNE_INTERVAL_MS = 60_000L
    }
}

/**
 * 头像抓取器：校验来源 → 命中缓存直接返回 → 否则下载并落盘，最终以 `data:` URL 交付。
 *
 * 与浏览器侧取图相比，这里能做浏览器做不到的事：**出口白名单 + 解析后 IP 判定 + 体积上限 + 磁盘缓存**。
 */
internal class AvatarFetcher(
    private val context: PlatformContext,
    private val cache: AvatarCache,
    private val allowedHosts: Set<String>,
    private val maxBytes: Int,
    private val userAgent: String
) {

    suspend fun fetchAsDataUrl(cacheKey: String, url: String): String? {
        cache.read(cacheKey)?.let { return toDataUrl(it) }
        val bytes = download(url) ?: return null
        cache.write(cacheKey, bytes)
        return toDataUrl(bytes)
    }

    /**
     * 取原始字节（不做 data URL 编码）。
     *
     * 皮肤漏斗需要原始 PNG 去裁头，因此不能复用 [fetchAsDataUrl]。
     * [cacheKey] 为空则不读写缓存（用于会话服务那类一次性、非图片的响应）。
     */
    suspend fun fetchBytes(cacheKey: String?, url: String): ByteArray? {
        if (cacheKey != null) cache.read(cacheKey)?.let { return it }
        val bytes = download(url) ?: return null
        if (cacheKey != null) cache.write(cacheKey, bytes)
        return bytes
    }

    /** 只读缓存（供漏斗存放**已裁切**的头像，命中时连抓取与裁切都省掉）。 */
    fun readCached(cacheKey: String): ByteArray? = cache.read(cacheKey)

    /** 写入缓存。 */
    fun writeCache(cacheKey: String, bytes: ByteArray) = cache.write(cacheKey, bytes)

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        // 逐跳校验的重定向跟随：自动跳转会让白名单校验只作用于初始 URL，
        // 被放行的主机再 302 到内网地址即可绕过（SSRF）。
        val conn = SafeHttp.openValidated(
            url = url,
            allowedHosts = allowedHosts,
            userAgent = userAgent,
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            log = { context.log(LogLevel.WARN, it) }
        ) ?: return@withContext null

        try {
            val declared = conn.contentLengthLong
            if (declared > maxBytes) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 头像体积 ${declared}B 超过上限 ${maxBytes}B，已拒绝。")
                return@withContext null
            }

            val buffer = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    if (buffer.size() + read > maxBytes) throw IOException("头像体积超过上限 ${maxBytes}B")
                    buffer.write(chunk, 0, read)
                }
            }
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) return@withContext null
            // 内容校验：拿 200 返回一张 HTML 错误页/占位页也必须拒绝，
            // 否则坏内容会以 PNG 名义写进缓存并被浏览器当作图片使用。
            if (!SafeHttp.looksLikeImage(bytes)) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 响应内容不是图片，已拒绝：$url")
                return@withContext null
            }
            bytes
        } catch (e: Exception) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 头像下载失败 ($url): ${e.message}")
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /** data URL 前缀按**实际内容**给出（此前硬编码 image/png，与嗅探允许的 JPEG/GIF/WEBP 不符）。 */
    private fun toDataUrl(bytes: ByteArray): String =
        "data:" + (SafeHttp.mimeOf(bytes) ?: "image/png") + ";base64," +
            Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3000
        const val READ_TIMEOUT_MS = 3000
    }
}
