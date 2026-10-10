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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderSecurity.toBrowserSafeUrl
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 主题解析与模板下载。
 *
 * 与旧实现的关键差别：
 * - [resolveThemeEntry] 返回 **可空**，失败即快速失败 —— 旧实现下载失败后仍返回一个**不存在**的
 *   `default/index.html`，导致调用方导航到空文件并白等一次导航超时；
 * - 下载改为 `Dispatchers.IO` + `disconnect()` + 体积上限 + **原子替换** + **检查每个返回值**；
 * - `template_url` 仅接受 **https**（旧实现允许 `file:` 等任意 scheme）；
 * - 开发期目录改为**显式可选配置**（旧实现硬编码 `File("web-t/dist")`，既相对进程 CWD、
 *   又会被 `file://` 沙箱拦掉，属双重失效）。
 */
internal class ThemeManager(
    private val context: PlatformContext,
    private val runtimeWebDir: File,
    /** 开发期直读目录；为空表示禁用。 */
    private val devWebDir: File? = null,
    /** 下载 User-Agent；由门户装配时注入共享常量，避免硬编码散落。 */
    private val userAgent: String = DEFAULT_RENDER_USER_AGENT
) {
    private val downloadMutex = Mutex()

    /** 允许被无头浏览器以 `file://` 读取的根目录（用于收紧沙箱）。 */
    val allowedFileRoots: List<File>
        get() = buildList {
            add(runtimeWebDir)
            devWebDir?.let { add(it) }
        }

    /**
     * 解析主题入口文件。
     *
     * 查找顺序：`<theme>/index.html` → `<theme>/<route>.html` → 开发期目录 → 下载 → 回退 `default`。
     * 全部失败返回 `null`（调用方应直接跳过 Playwright 并降级 AWT，而不是继续导航）。
     */
    suspend fun resolveThemeEntry(theme: String, viewRoute: String): File? {
        val sanitized = sanitizeName(theme)
        if (sanitized == null) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 非法主题名 '${theme}'（仅允许字母/数字/下划线/短横线），已回退 $DEFAULT_THEME。")
        }
        val themeName = sanitized ?: DEFAULT_THEME

        localEntry(themeName, viewRoute)?.let { return it }

        devWebDir?.let { dev ->
            val index = File(dev, "index.html")
            if (index.isFile && index.length() > 0L) return index
        }

        val downloaded = downloadMutex.withLock {
            localEntry(themeName, viewRoute) ?: run {
                val index = File(File(runtimeWebDir, themeName), "index.html")
                if (downloadTemplate(index)) localEntry(themeName, viewRoute) else null
            }
        }
        if (downloaded != null) return downloaded

        if (themeName != DEFAULT_THEME) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 主题 '$themeName' 不可用，自动回退到 '$DEFAULT_THEME' 主题。")
            return resolveThemeEntry(DEFAULT_THEME, viewRoute)
        }

        context.log(LogLevel.WARN, "[PlumBot-Render] 默认主题不可用（本地缺失且下载失败），本次渲染跳过 Playwright。")
        return null
    }

    private fun localEntry(themeName: String, viewRoute: String): File? {
        val dir = File(runtimeWebDir, themeName)
        if (!dir.isDirectory) return null

        val index = File(dir, "index.html")
        if (index.isFile && index.length() > 0L) return index

        val route = sanitizeName(viewRoute) ?: return null
        val routeFile = File(dir, "$route.html")
        if (routeFile.isFile && routeFile.length() > 0L) return routeFile
        return null
    }

    private fun sanitizeName(raw: String): String? {
        val trimmed = raw.trim()
        return if (trimmed.isNotEmpty() && NAME_PATTERN.matches(trimmed)) trimmed else null
    }

    /**
     * 下载单文件模板并原子落盘。任何一步失败都返回 false（不留下 `.tmp`、不返回半成品）。
     */
    private suspend fun downloadTemplate(destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        val rawUrl = context.config.getString("feature", "img", "playwright", "template_url")
        if (rawUrl.isNullOrBlank()) return@withContext false

        val uri = runCatching { URI(rawUrl) }.getOrNull()
        if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) {
            context.log(LogLevel.WARN, "[PlumBot-Render] template_url 必须是 https 直链，已拒绝：$rawUrl")
            return@withContext false
        }

        val configuredTimeout = context.config.getInteger("feature", "img", "playwright", "download_timeout_ms")
        val timeoutMs = if (configuredTimeout <= 0) DEFAULT_TIMEOUT_MS else configuredTimeout
        val configuredMax = context.config.getInteger("feature", "img", "playwright", "template_max_bytes")
        val maxBytes = if (configuredMax <= 0) DEFAULT_MAX_BYTES else configuredMax

        // R28：校验和配置先解析 —— **格式非法必须失败关闭**（静默跳过会把安全开关变成安慰剂）
        val expected = when (
            val parsed = TemplateIntegrity.parse(
                context.config.getString("feature", "img", "playwright", "template_sha256")
            )
        ) {
            is TemplateIntegrity.Expected.Malformed -> {
                context.log(LogLevel.ERROR, TemplateIntegrity.malformedMessage(parsed.raw))
                return@withContext false
            }

            else -> parsed
        }

        val parent = destinationFile.parentFile ?: return@withContext false
        parent.mkdirs()
        val tempFile = File(parent, "${destinationFile.name}.tmp")

        // 逐跳校验（与头像下载同一套）：模板 URL 由配置指定，但同样不该被 302 引到内网。
        // 模板来源是无白名单限制的直链，因此这里只做 scheme + 内网/回环判定。
        val conn = SafeHttp.openValidatedWithoutAllowlist(
            url = rawUrl,
            userAgent = userAgent,
            connectTimeoutMs = timeoutMs,
            readTimeoutMs = timeoutMs,
            accepted = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            log = { context.log(LogLevel.WARN, it) }
        ) ?: return@withContext false

        try {
            val declared = conn.contentLengthLong
            if (declared > maxBytes) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 模板声明体积 ${declared}B 超过上限 ${maxBytes}B，已拒绝。")
                return@withContext false
            }

            var total = 0L
            conn.inputStream.use { input ->
                tempFile.outputStream().use { out ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw IOException("模板体积超过上限 ${maxBytes}B")
                        out.write(buffer, 0, read)
                    }
                }
            }

            if (total <= 0L) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 模板下载内容为空：$rawUrl")
                return@withContext false
            }

            // R28：**落盘前**校验内容（校验失败就不替换模板，旧的可用模板继续生效）
            if (expected is TemplateIntegrity.Expected.Valid) {
                val actual = TemplateIntegrity.sha256(tempFile)
                if (!TemplateIntegrity.matches(actual, expected.hex)) {
                    context.log(LogLevel.ERROR, TemplateIntegrity.mismatchMessage(expected.hex, actual))
                    return@withContext false
                }
            }

            if (!moveAtomically(tempFile, destinationFile)) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 模板落盘失败：${destinationFile.absolutePath}")
                return@withContext false
            }
            true
        } catch (e: Exception) {
            context.log(LogLevel.WARN, "[PlumBot-Render] 模板下载异常：${e.message}")
            false
        } finally {
            runCatching { conn?.disconnect() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    /**
     * 原子替换目标文件；文件系统不支持 `ATOMIC_MOVE` 时退化为覆盖移动。
     * 旧实现直接用 `renameTo` 且**丢弃返回值**，失败时会静默留下不存在的目标文件。
     */
    private fun moveAtomically(source: File, target: File): Boolean {
        val atomic = runCatching {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        }
        if (atomic.isSuccess) return target.isFile && target.length() > 0L

        val fallback = runCatching {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        fallback.onFailure {
            context.log(LogLevel.WARN, "[PlumBot-Render] 模板文件移动失败：${it.message}")
        }
        return fallback.isSuccess && target.isFile && target.length() > 0L
    }

    private companion object {
        const val DEFAULT_THEME = "default"
        const val DEFAULT_TIMEOUT_MS = 10_000
        const val DEFAULT_MAX_BYTES = 8 * 1024 * 1024

        val NAME_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
