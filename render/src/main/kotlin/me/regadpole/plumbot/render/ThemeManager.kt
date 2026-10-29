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
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 主题解析失败的类型：渲染器据此映射 `PW_TEMPLATE_MISSING` / `PW_TEMPLATE_INVALID`。 */
internal enum class ThemeFailureKind { MISSING, INVALID }

/** 带原因的主题解析结果（不再用 null 把失败原因吞掉）。 */
internal sealed interface ThemeResolution {
    data class Available(val file: File) : ThemeResolution
    data class Failed(val kind: ThemeFailureKind, val detail: String) : ThemeResolution
}

/**
 * 主题解析与模板下载。
 *
 * 查找顺序：`<theme>/index.html` → `<theme>/<route>.html` → `dev_web_dir/index.html`
 * → 候选地址下载 → 回退 `default`。
 *
 * 关键约束：
 * - [resolveTheme] 返回 [ThemeResolution]，**失败原因不丢失**，旧模板在失败时保持可用；
 * - 候选由 [TemplateMirrors] 给出（官方直链优先 + 3 个公共加速站，`template_urls`
 *   非空即完全覆盖），逐个尝试，全失败才报错并给出手动命令；
 * - 候选只接受 **https**；下载有体积上限、sha256 校验与原子替换；
 * - 校验失配**直接失败、不换镜像重试**（否则改一个镜像就能绕过校验）；
 * - 未内置模板，也不在 Playwright 启动时隐式安装任何东西。
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

    /** 旧版入口：保留给既有调用方，新代码用 [resolveTheme] 取失败原因。 */
    suspend fun resolveThemeEntry(theme: String, viewRoute: String): File? =
        (resolveTheme(theme, viewRoute) as? ThemeResolution.Available)?.file

    suspend fun resolveTheme(theme: String, viewRoute: String): ThemeResolution {
        val sanitized = sanitizeName(theme)
        if (sanitized == null) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 非法主题名 '${theme}'（仅允许字母/数字/下划线/短横线），已回退 $DEFAULT_THEME。"
            )
        }
        val themeName = sanitized ?: DEFAULT_THEME

        localEntry(themeName, viewRoute)?.let { return ThemeResolution.Available(it) }

        devWebDir?.let { dev ->
            val index = File(dev, "index.html")
            if (index.isFile && index.length() > 0L) return ThemeResolution.Available(index)
        }

        val resolved = downloadMutex.withLock {
            localEntry(themeName, viewRoute)?.let { return@withLock ThemeResolution.Available(it) }
            val index = File(File(runtimeWebDir, themeName), "index.html")
            when (val download = downloadTemplate(index)) {
                TemplateDownload.Downloaded ->
                    localEntry(themeName, viewRoute)?.let { ThemeResolution.Available(it) }
                        ?: ThemeResolution.Failed(
                            ThemeFailureKind.MISSING,
                            "模板下载完成但入口仍不可读：${index.absolutePath}"
                        )

                is TemplateDownload.Failure -> ThemeResolution.Failed(download.kind, download.detail)
            }
        }
        if (resolved is ThemeResolution.Available) return resolved

        val failure = resolved as ThemeResolution.Failed
        if (themeName != DEFAULT_THEME) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] 主题 '$themeName' 不可用（${failure.detail}），自动回退到 '$DEFAULT_THEME' 主题。"
            )
            return resolveTheme(DEFAULT_THEME, viewRoute)
        }

        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 默认主题不可用（${failure.detail}）。" +
                "本次渲染将失败并报 PW_TEMPLATE_MISSING/PW_TEMPLATE_INVALID，不再静默降级。"
        )
        return failure
    }

    /**
     * 手动安装/修复模板的命令（复制到服务器终端即可执行）。
     *
     * 命令下载的是**第一个候选**（官方直链优先）。若它 404，可把已发布的单文件
     * `index.html` 直接复制到目标路径，或在 `template_urls` 里写一个可访问的地址。
     */
    fun manualTemplateCommand(theme: String = DEFAULT_THEME): String {
        val themeName = sanitizeName(theme) ?: DEFAULT_THEME
        val dir = File(runtimeWebDir, themeName).absolutePath
        val target = File(dir, "index.html").absolutePath
        val url = templateCandidates().firstOrNull().orEmpty()
        val windows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

        if (url.isBlank() || !url.startsWith("https://", ignoreCase = true)) {
            return if (windows) {
                "New-Item -ItemType Directory -Force -Path '${psQuote(dir)}' | Out-Null; " +
                    "Copy-Item '<你的模板 index.html>' '${psQuote(target)}'"
            } else {
                "mkdir -p ${shQuote(dir)} && cp '<你的模板 index.html>' ${shQuote(target)}"
            }
        }

        return if (windows) {
            "New-Item -ItemType Directory -Force -Path '${psQuote(dir)}' | Out-Null; " +
                "Invoke-WebRequest -Uri '${psQuote(url)}' -OutFile '${psQuote(target)}'"
        } else {
            "mkdir -p ${shQuote(dir)} && curl -fsSL ${shQuote(url)} -o ${shQuote(target)}"
        }
    }

    fun templateUrl(): String? =
        context.config.getString("render", "playwright", "template_url")?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 当前配置下的候选序列。
     *
     * 每次下载时实时读取配置：改 `template_url` / `template_urls` 无需重建引擎。
     */
    fun templateCandidates(): List<String> =
        TemplateMirrors.candidates(
            templateUrl(),
            context.config.getStringList("render", "playwright", "template_urls")
        )

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

    private sealed interface TemplateDownload {
        data object Downloaded : TemplateDownload
        data class Failure(val kind: ThemeFailureKind, val detail: String) : TemplateDownload
    }

    /** 单个候选的一次尝试：成功 / 这个候选不可用（换下一个）/ 必须整体失败（不再试）。 */
    private sealed interface CandidateAttempt {
        data class Success(val bytes: Long) : CandidateAttempt
        data class Retry(val reason: String) : CandidateAttempt
        data class Fatal(val kind: ThemeFailureKind, val detail: String) : CandidateAttempt
    }

    /**
     * 逐个候选下载单文件模板并原子落盘，全失败返回**带原因**的 Failure（不留 `.tmp`）。
     *
     * 失败分两类：
     * - 候选不可用（网络、非 2xx、空内容、超体积、落盘失败）→ 换下一个候选；
     * - 内容校验失配 → **立即整体失败**，不换镜像重试（否则等于允许绕过完整性校验），
     *   旧模板原样保留。
     */
    private suspend fun downloadTemplate(destinationFile: File): TemplateDownload = withContext(Dispatchers.IO) {
        val candidates = templateCandidates()
        if (candidates.isEmpty()) {
            return@withContext TemplateDownload.Failure(
                ThemeFailureKind.MISSING,
                "本地没有缓存模板，且 render.playwright.template_url / template_urls 为空"
            )
        }

        // 校验和配置先解析：**格式非法必须失败关闭**（静默跳过会把安全开关变成安慰剂）
        val expected = when (
            val parsed = TemplateIntegrity.parse(
                context.config.getString("render", "playwright", "template_sha256")
            )
        ) {
            is TemplateIntegrity.Expected.Malformed -> {
                val message = TemplateIntegrity.malformedMessage(parsed.raw)
                context.log(LogLevel.ERROR, message)
                return@withContext TemplateDownload.Failure(ThemeFailureKind.INVALID, message)
            }

            else -> parsed
        }

        val configuredTimeout = context.config.getInteger("render", "playwright", "download_timeout_ms")
        val timeoutMs = if (configuredTimeout <= 0) DEFAULT_TIMEOUT_MS else configuredTimeout
        val configuredMax = context.config.getInteger("render", "playwright", "template_max_bytes")
        val maxBytes = if (configuredMax <= 0) DEFAULT_MAX_BYTES else configuredMax

        val parent = destinationFile.parentFile
            ?: return@withContext TemplateDownload.Failure(
                ThemeFailureKind.MISSING,
                "模板目标目录不存在：${destinationFile.absolutePath}"
            )
        parent.mkdirs()
        val tempFile = File(parent, "${destinationFile.name}.tmp")

        // 开始 / 完成各一行 INFO；候选切换与逐跳明细一律 DEBUG。
        context.log(LogLevel.INFO, "[PlumBot-Render] 开始下载模板（共 ${candidates.size} 个候选）：${candidates.first()}")

        val failures = mutableListOf<String>()
        candidates.forEachIndexed { index, rawUrl ->
            val hop = index + 1
            val rejection = TemplateMirrors.rejection(rawUrl)
            if (rejection != null) {
                context.log(LogLevel.DEBUG, "[PlumBot-Render] 跳过第 $hop/${candidates.size} 个候选（$rejection）：$rawUrl")
                failures += "第 $hop 个：$rejection"
                return@forEachIndexed
            }
            if (hop > 1) {
                context.log(LogLevel.DEBUG, "[PlumBot-Render] 改用第 $hop/${candidates.size} 个候选：$rawUrl")
            }

            when (val attempt = fetchCandidate(rawUrl, destinationFile, tempFile, expected, timeoutMs, maxBytes)) {
                is CandidateAttempt.Success -> {
                    context.log(
                        LogLevel.INFO,
                        "[PlumBot-Render] 模板下载完成（第 $hop/${candidates.size} 个候选，${attempt.bytes}B）：" +
                            "$rawUrl -> ${destinationFile.absolutePath}"
                    )
                    return@withContext TemplateDownload.Downloaded
                }

                is CandidateAttempt.Fatal -> return@withContext TemplateDownload.Failure(attempt.kind, attempt.detail)

                is CandidateAttempt.Retry -> {
                    context.log(LogLevel.DEBUG, "[PlumBot-Render] 候选不可用（第 $hop/${candidates.size} 个）：$rawUrl —— ${attempt.reason}")
                    failures += "第 $hop 个：${attempt.reason}"
                }
            }
        }

        val summary = failures.joinToString("；")
        context.log(
            LogLevel.WARN,
            "[PlumBot-Render] 模板下载失败：所有候选均不可用（$summary）。" +
                "可手动安装：${manualTemplateCommand()}"
        )
        TemplateDownload.Failure(ThemeFailureKind.MISSING, "模板下载失败（所有候选均不可用：$summary）")
    }

    private suspend fun fetchCandidate(
        rawUrl: String,
        destinationFile: File,
        tempFile: File,
        expected: TemplateIntegrity.Expected,
        timeoutMs: Int,
        maxBytes: Int
    ): CandidateAttempt = withContext(Dispatchers.IO) {
        // 逐跳校验（与头像下载同一套）：仅 https；起点不做白名单限制（模板地址由服主配置）。
        val conn = SafeHttp.openValidatedWithoutAllowlist(
            url = rawUrl,
            userAgent = userAgent,
            connectTimeoutMs = timeoutMs,
            readTimeoutMs = timeoutMs,
            accepted = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            log = { level, message -> context.log(level, message) }
        ) ?: return@withContext CandidateAttempt.Retry("网络不可达或 HTTP 非 2xx")

        try {
            val declared = conn.contentLengthLong
            if (declared > maxBytes) {
                context.log(LogLevel.WARN, "[PlumBot-Render] 模板声明体积 ${declared}B 超过上限 ${maxBytes}B，已拒绝：$rawUrl")
                return@withContext CandidateAttempt.Retry("声明体积 ${declared}B 超过上限 ${maxBytes}B")
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

            if (total <= 0L) return@withContext CandidateAttempt.Retry("下载内容为空")

            // **落盘前**校验内容：不匹配 = 整体失败，不换镜像重试，旧模板继续生效。
            if (expected is TemplateIntegrity.Expected.Valid) {
                val actual = TemplateIntegrity.sha256(tempFile)
                if (!TemplateIntegrity.matches(actual, expected.hex)) {
                    val message = TemplateIntegrity.mismatchMessage(expected.hex, actual, rawUrl)
                    context.log(LogLevel.ERROR, message)
                    return@withContext CandidateAttempt.Fatal(ThemeFailureKind.INVALID, message)
                }
            }

            if (!moveAtomically(tempFile, destinationFile)) {
                return@withContext CandidateAttempt.Retry("落盘失败：${destinationFile.absolutePath}")
            }
            CandidateAttempt.Success(total)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            context.log(LogLevel.DEBUG, "[PlumBot-Render] 候选下载异常：$rawUrl —— ${e.message}")
            CandidateAttempt.Retry("下载异常：${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { conn.disconnect() }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    /**
     * 原子替换目标文件；文件系统不支持 `ATOMIC_MOVE` 时退化为覆盖移动。
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

        /** PowerShell 单引号字符串：内部单引号翻倍。 */
        fun psQuote(value: String): String = value.replace("'", "''")

        /** POSIX shell 单引号字符串。 */
        fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
