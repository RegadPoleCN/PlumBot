// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.io.path.absolutePathString
import me.regadpole.plumbot.api.PublicApi
import me.regadpole.plumbot.render.install.BrowserDecision
import me.regadpole.plumbot.render.install.BrowserStrategy
import me.regadpole.plumbot.render.pipeline.ImageRenderManager

/**
 * Playwright 失败码（稳定契约，供 `/plumbot render doctor`、日志与文档引用）。
 *
 * 所有失败都必须落在这些码之一；UI 层可据此区分"配置错 / 缺依赖 / 缺浏览器 /
 * 模板错 / 导航或截图失败"，而不是只有一句"playwright 无法使用"。
 */
@PublicApi
public object PlaywrightErrorCodes {
    public const val CONFIG_INVALID: String = "PW_CONFIG_INVALID"
    public const val DEPENDENCY_MISSING: String = "PW_DEPENDENCY_MISSING"
    public const val BROWSER_MISSING: String = "PW_BROWSER_MISSING"
    public const val DRIVER_START_FAILED: String = "PW_DRIVER_START_FAILED"
    public const val LAUNCH_FAILED: String = "PW_LAUNCH_FAILED"
    public const val TEMPLATE_MISSING: String = "PW_TEMPLATE_MISSING"
    public const val TEMPLATE_INVALID: String = "PW_TEMPLATE_INVALID"
    public const val NAVIGATION_FAILED: String = "PW_NAVIGATION_FAILED"
    public const val SCREENSHOT_FAILED: String = "PW_SCREENSHOT_FAILED"

    /** 安装器专用：下载/解包/安装进程失败。 */
    public const val INSTALL_FAILED: String = "PW_INSTALL_FAILED"

    /** 渲染预算内没等到页面池（不是浏览器/模板配置错误）。 */
    public const val RENDER_TIMEOUT: String = "PW_RENDER_TIMEOUT"

    public val ALL: Set<String> = linkedSetOf(
        CONFIG_INVALID, DEPENDENCY_MISSING, BROWSER_MISSING, DRIVER_START_FAILED,
        LAUNCH_FAILED, TEMPLATE_MISSING, TEMPLATE_INVALID, NAVIGATION_FAILED,
        SCREENSHOT_FAILED, INSTALL_FAILED, RENDER_TIMEOUT
    )
}

/**
 * 可读的 Playwright 失败。message 同时包含 code、阶段、原因、处理建议与手动命令，
 * 便于 [me.regadpole.plumbot.render.pipeline.ImageRenderManager] 现有的 WARN 打印直接给出可操作信息。
 */
@PublicApi
public class PlaywrightRenderException(
    public val code: String,
    public val stage: String,
    public val detail: String,
    public val hint: String? = null,
    public val command: String? = null,
    /** 渲染路由（供降级日志按 route 聚合）；非渲染阶段为 null。 */
    public val route: String? = null,
    cause: Throwable? = null
) : RuntimeException(buildMessage(code, stage, detail, hint, command, route), cause) {

    private companion object {
        fun buildMessage(code: String, stage: String, detail: String, hint: String?, command: String?, route: String?): String =
            buildString {
                append('[').append(code).append("] stage=").append(stage)
                route?.takeIf { it.isNotBlank() }?.let { append(" route=").append(it) }
                append(": ").append(detail)
                hint?.takeIf { it.isNotBlank() }?.let { append(" | 处理建议: ").append(it) }
                command?.takeIf { it.isNotBlank() }?.let { append(" | 手动命令: ").append(it) }
            }
    }
}

/** `render.playwright.install_shell` 的取值。 */
@PublicApi
public enum class PlaywrightShell(public val configValue: String, public val cliArg: String?) {
    /** 只安装完整 Chromium，不装 headless shell（体积更小，默认）。 */
    NO_SHELL("no-shell", "--no-shell"),

    /** 只安装 headless shell（更小，但部分有头路径不可用）。 */
    ONLY_SHELL("only-shell", "--only-shell"),

    /** Playwright 默认行为：完整 Chromium + headless shell。 */
    FULL("full", null);

    public companion object {
        /** 非法值返回 null，由调用方转成 `PW_CONFIG_INVALID`，绝不静默回退。 */
        public fun parse(raw: String?): PlaywrightShell? {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return NO_SHELL
            return entries.firstOrNull { it.configValue == value }
        }
    }
}

/** 安装/诊断参数（与平台配置解耦，方便命令层与测试直接构造）。 */
@PublicApi
public data class PlaywrightInstallOptions(
    val browsersPath: String,
    val downloadHost: String? = null,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val shell: PlaywrightShell = PlaywrightShell.NO_SHELL,
    /** null = Linux 自动加 --with-deps，其他平台不加。 */
    val withDeps: Boolean? = null,
    /** 显式 driver-bundle 路径；null 时从类路径定位。 */
    val driverBundlePath: String? = null,
    val javaHome: String? = null,
    /** 仅诊断使用：与 renderer 相同的浏览器覆盖配置。 */
    val customChromePath: String? = null,
    /** 仅诊断使用：与 renderer 相同的品牌通道。 */
    val browserChannel: String? = null,
    val osName: String = System.getProperty("os.name").orEmpty()
) {
    public companion object {
        public const val DEFAULT_TIMEOUT_SECONDS: Int = 600
    }
}

/** 安装命令的构造结果（命令文本可原样展示给服主）。 */
@PublicApi
public data class PlaywrightInstallCommand(
    val argv: List<String>,
    val displayText: String,
    val environment: Map<String, String>
)

/** `/plumbot render doctor` 使用的只读诊断结果。 */
@PublicApi
public data class PlaywrightDiagnosis(
    val ready: Boolean,
    val code: String?,
    val summary: String,
    val details: List<String>,
    val manualCommand: String,
    val browserPath: String?,
    val driverBundlePath: String?,
    val playwrightJarPath: String?
)

/** `/plumbot render install` 的返回结果。 */
@PublicApi
public data class PlaywrightInstallResult(
    val success: Boolean,
    val code: String?,
    val message: String,
    val command: String,
    val outputTail: List<String>,
    val exitCode: Int?,
    val timedOut: Boolean,
    val installedBrowserPath: String?
)

/**
 * Playwright 浏览器安装器（命令层 API）。
 *
 * 设计约束：
 * - **不在** `Playwright.create()` 里隐式安装浏览器。安装是显式动作，失败也显式；
 * - 命令固定为 `node <driver>/package/cli.js install chromium`，不下载 firefox/webkit；
 * - 注入 `PLAYWRIGHT_BROWSERS_PATH` 与 `PLAYWRIGHT_DOWNLOAD_HOST`，并**移除**
 *   `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD`（否则安装器会什么都不做）；
 * - Linux 默认追加 `--with-deps`；安装失败不回滚已有目录、不锁死，可重复执行；
 * - 超时强杀，返回输出尾部 20 行，避免"卡住十分钟没有任何日志"。
 */
@PublicApi
public class PlaywrightInstaller(
    private val classLoader: ClassLoader? = null,
    private val log: (String) -> Unit = {}
) {

    public fun diagnose(options: PlaywrightInstallOptions): PlaywrightDiagnosis {
        val driverBundle = locateDriverBundle(options)
        val playwrightJar = findPlaywrightJar(driverBundle)
        val managed = findManagedBrowser(options)
        val detected = BrowserStrategy.detectSystemBrowserPath(options.osName)
        val decision = BrowserStrategy.decide(
            customPath = options.customChromePath,
            explicitChannel = options.browserChannel,
            detectedPath = detected,
            managedPath = managed
        )
        val effectiveBrowser = (decision as? BrowserDecision.Executable)?.path
        val code = when {
            driverBundle == null || playwrightJar == null -> PlaywrightErrorCodes.DEPENDENCY_MISSING
            decision is BrowserDecision.InvalidConfig -> PlaywrightErrorCodes.CONFIG_INVALID
            decision is BrowserDecision.Missing -> PlaywrightErrorCodes.BROWSER_MISSING
            else -> null
        }
        // 目录/镜像/超时由命令层 describeOptions() 打印，这里再打一遍就是重复行。
        val details = buildList {
            add("driver-bundle=${driverBundle?.absolutePathString() ?: "未找到"}")
            add("playwright.jar=${playwrightJar?.absolutePathString() ?: "未找到"}")
            add("system-chromium=${detected ?: "未探测到"}")
            add("managed-chromium=${managed ?: "未安装"}")
            // 运行库缺失时 system-chromium 永远用不上：不标注会让服主以为"检测到 Edge 就算就绪"。
            val decisionText = decisionLabel(decision)
            add(
                if (code == PlaywrightErrorCodes.DEPENDENCY_MISSING) {
                    "decision=$decisionText（需先补齐 Playwright 运行库才可用）"
                } else {
                    "decision=$decisionText"
                }
            )
        }
        val command = manualCommand(options)
        val summary = when (code) {
            PlaywrightErrorCodes.DEPENDENCY_MISSING ->
                "Playwright 运行库不完整：缺少 playwright.jar 或 driver-bundle（Libby 未下载/ClassLoader 不可见）"
            PlaywrightErrorCodes.CONFIG_INVALID ->
                (decision as BrowserDecision.InvalidConfig).detail
            PlaywrightErrorCodes.BROWSER_MISSING ->
                "Playwright 运行库已就位，但没有可用浏览器：${(decision as BrowserDecision.Missing).detail}"
            else -> "Playwright 已就绪：${decisionLabel(decision)}"
        }
        return PlaywrightDiagnosis(
            ready = code == null,
            code = code,
            summary = summary,
            details = details,
            manualCommand = command,
            browserPath = effectiveBrowser ?: managed,
            driverBundlePath = driverBundle?.absolutePathString(),
            playwrightJarPath = playwrightJar?.absolutePathString()
        )
    }

    /**
     * 显式安装 Chromium。
     *
     * @param force false 时若已检测到可用浏览器则直接返回成功（幂等）。
     */
    public fun install(options: PlaywrightInstallOptions, force: Boolean = false): PlaywrightInstallResult {
        validate(options)?.let { return it }
        if (!force) {
            findManagedBrowser(options)?.let { existing ->
                return PlaywrightInstallResult(
                    success = true,
                    code = null,
                    message = "Chromium 已安装，无需重复下载：$existing",
                    command = manualCommand(options),
                    outputTail = emptyList(),
                    exitCode = null,
                    timedOut = false,
                    installedBrowserPath = existing
                )
            }
        }

        val driverBundle = locateDriverBundle(options)
            ?: return failure(
                PlaywrightErrorCodes.DEPENDENCY_MISSING,
                "Playwright 运行库未就位：找不到 driver-bundle。" +
                    "它不在插件 jar 里，必须先由 Libby 下载 com.microsoft.playwright:driver-bundle（约 190 MB）；" +
                    "若下载失败，/plumbot render doctor 会打印具体原因（仓库不可达、镜像缺件等）。",
                emptyList(), null, false,
                runCatching { manualCommand(options) }.getOrDefault(""),
            )

        val platformDir = platformDirectory(options.osName)
            ?: return failure(
                PlaywrightErrorCodes.CONFIG_INVALID,
                "当前平台不受 Playwright 1.49 driver 支持：os=${options.osName}, arch=${System.getProperty("os.arch")}",
                emptyList(), null, false
            )

        val tempDir = try {
            Files.createTempDirectory("plumbot-playwright-install-")
        } catch (e: IOException) {
            return failure(
                PlaywrightErrorCodes.INSTALL_FAILED,
                "无法创建临时解包目录：${e.message}",
                emptyList(), null, false
            )
        }

        try {
            val extracted = runCatching { extractDriver(driverBundle, platformDir, tempDir) }
                .getOrElse { e ->
                    return failure(
                        PlaywrightErrorCodes.DEPENDENCY_MISSING,
                        "解包 driver-bundle 失败：${e.message}",
                        emptyList(), null, false
                    )
                }
            val node = extracted.resolve(if (isWindows(options.osName)) "node.exe" else "node")
            val cli = extracted.resolve("package").resolve("cli.js")
            if (!Files.isRegularFile(node) || !Files.isRegularFile(cli)) {
                return failure(
                    PlaywrightErrorCodes.DEPENDENCY_MISSING,
                    "driver-bundle 缺少 node 或 cli.js（platform=$platformDir，解包目录=${extracted.absolutePathString()}）",
                    emptyList(), null, false
                )
            }
            runCatching { node.toFile().setExecutable(true) }

            val invocation = buildInvocation(node.toFile(), cli.toFile(), options)
            log("执行安装命令：${invocation.displayText}")
            log("环境：${invocation.environment.entries.joinToString { "${it.key}=${it.value}" }}")

            val timeout = if (options.timeoutSeconds > 0) options.timeoutSeconds else PlaywrightInstallOptions.DEFAULT_TIMEOUT_SECONDS
            val process = try {
                ProcessBuilder(invocation.argv)
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .also { it.environment().apply { putAll(invocation.environment); remove("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD") } }
                    .start()
            } catch (e: IOException) {
                return failure(
                    PlaywrightErrorCodes.INSTALL_FAILED,
                    "无法启动安装进程：${e.message}",
                    emptyList(), null, false
                )
            }

            val collector = OutputCollector(process, 20)
            val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                runCatching { process.waitFor(5, TimeUnit.SECONDS) }
            }
            collector.join(3_000)
            val output = collector.snapshot()
            val exit = if (finished) process.exitValue() else null
            val installed = findManagedBrowser(options)
            val success = finished && exit == 0 && installed != null
            val detail = when {
                !finished -> "安装超时（${timeout}s），已强制终止；可调大 render.playwright.install_timeout_seconds 或检查镜像"
                exit != 0 -> "安装进程退出码 $exit"
                installed == null -> "安装进程返回 0，但 browsers_path 下仍未找到 Chromium"
                else -> "安装完成：$installed"
            }
            return PlaywrightInstallResult(
                success = success,
                code = if (success) null else PlaywrightErrorCodes.INSTALL_FAILED,
                message = buildString {
                    append(detail)
                    if (output.isNotEmpty()) append("\n输出（尾部）：\n").append(output.joinToString("\n"))
                    if (!success) append("\n手动命令：").append(manualCommand(options))
                },
                command = invocation.displayText,
                outputTail = output,
                exitCode = exit,
                timedOut = !finished,
                installedBrowserPath = installed
            )
        } finally {
            runCatching { tempDir.toFile().deleteRecursively() }
        }
    }

    /** 供 `/plumbot render doctor` 与错误消息复用的手动安装命令。 */
    public fun manualCommand(options: PlaywrightInstallOptions): String {
        val bundle = locateDriverBundle(options)
        val bundlePath = bundle?.absolutePathString() ?: "<driver-bundle-1.49.0.jar>"
        val driverJar = findDriverJar(bundle)?.absolutePathString() ?: "<driver-1.49.0.jar>"
        val playwright = findPlaywrightJar(bundle)?.absolutePathString() ?: "<playwright-1.49.0.jar>"
        // Playwright 1.49 的 CLI 同时需要三个 jar：Driver/DriverJar 接口在 driver.jar、
        // node+cli.js 在 driver-bundle.jar，API 与 CLI 入口在 playwright.jar。
        // 只给 playwright.jar（或两 jar）会在启动时报 NoClassDefFoundError。
        val classpath = listOf(driverJar, playwright, bundlePath).joinToString(File.pathSeparator)
        val java = javaExecutable(options)
        val argv = buildList {
            add(java)
            add("-cp")
            add(classpath)
            add("com.microsoft.playwright.CLI")
            add("install")
            add("chromium")
            options.shell.cliArg?.let(::add)
            if (withDeps(options)) add("--with-deps")
        }
        val env = environment(options)
        return if (isWindows(options.osName)) {
            val sets = env.entries.joinToString(" && ") { (key, value) -> "set \"$key=$value\"" }
            "$sets && ${argv.joinToString(" ") { cmdQuote(it) }}"
        } else {
            val prefix = env.entries.joinToString(" ") { (key, value) -> "$key=${shQuote(value)}" }
            "$prefix ${argv.joinToString(" ") { shQuote(it) }}"
        }
    }

    /** 解析 driver 平台目录；不支持的平台返回 null。 */
    public fun platformDirectory(osName: String = System.getProperty("os.name").orEmpty()): String? {
        val os = osName.lowercase()
        val arch = System.getProperty("os.arch").orEmpty().lowercase()
        return when {
            os.contains("win") -> "win32_x64"
            os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm64")) "mac-arm64" else "mac"
            else -> if (arch.contains("aarch64") || arch.contains("arm64")) "linux-arm64" else "linux"
        }
    }

    /** 定位 driver-bundle：显式路径 → 类路径上的 DriverJar → java.class.path。 */
    public fun locateDriverBundle(options: PlaywrightInstallOptions): Path? {
        val explicit = options.driverBundlePath?.trim().orEmpty()
        if (explicit.isNotEmpty()) {
            val file = File(explicit)
            return if (file.isFile) file.toPath() else null
        }
        val loaders = buildList {
            classLoader?.let(::add)
            Thread.currentThread().contextClassLoader?.let(::add)
            PlaywrightInstaller::class.java.classLoader?.let(::add)
            runCatching { Class.forName("com.microsoft.playwright.Playwright", false, classLoader ?: Thread.currentThread().contextClassLoader) }
                .getOrNull()?.classLoader?.let(::add)
        }.distinct()
        for (loader in loaders) {
            val location = runCatching {
                Class.forName(DRIVER_JAR_CLASS, false, loader)
                    .protectionDomain?.codeSource?.location?.toURI()
            }.getOrNull() ?: continue
            val path = runCatching { Path.of(location) }.getOrNull() ?: continue
            if (Files.isRegularFile(path) && path.fileName.toString().endsWith(".jar")) return path
        }
        return System.getProperty("java.class.path").orEmpty()
            .split(File.pathSeparatorChar)
            .asSequence()
            .map(::File)
            .filter { it.isFile && it.name.startsWith("driver-bundle") && it.name.endsWith(".jar") }
            .map { it.toPath() }
            .firstOrNull()
    }

    /** 定位 playwright.jar（CLI 入口）；不在时返回 null。 */
    public fun findPlaywrightJar(driverBundle: Path? = null): Path? {
        val loaders = listOfNotNull(
            classLoader,
            Thread.currentThread().contextClassLoader,
            PlaywrightInstaller::class.java.classLoader
        )
        for (loader in loaders) {
            val location = runCatching {
                Class.forName("com.microsoft.playwright.Playwright", false, loader)
                    .protectionDomain?.codeSource?.location?.toURI()
            }.getOrNull() ?: continue
            val path = runCatching { Path.of(location) }.getOrNull() ?: continue
            if (Files.isRegularFile(path) && path.fileName.toString().startsWith("playwright-")) return path
        }

        // 回退：在 driver-bundle 的兄弟/祖先目录里找同版本的 playwright jar。
        var cursor = driverBundle?.parent
        repeat(8) {
            val dir = cursor?.takeIf { Files.isDirectory(it) } ?: return@repeat
            dir.toFile().listFiles()?.firstOrNull {
                it.isFile && it.name.startsWith("playwright-") && it.name.endsWith(".jar")
            }?.let { return it.toPath() }
            val standard = dir.resolve("com/microsoft/playwright/playwright")
            if (Files.isDirectory(standard)) {
                standard.toFile().walkTopDown().maxDepth(4).firstOrNull {
                    it.isFile && it.name.startsWith("playwright-") && it.name.endsWith(".jar")
                }?.let { return it.toPath() }
            }
            cursor = dir.parent
        }
        return null
    }

    /** 定位 driver.jar（Driver/PreinstalledDriver 接口），CLI classpath 必需；不在时返回 null。 */
    public fun findDriverJar(driverBundle: Path? = null): Path? {
        val loaders = listOfNotNull(
            classLoader,
            Thread.currentThread().contextClassLoader,
            PlaywrightInstaller::class.java.classLoader
        )
        for (loader in loaders) {
            val location = runCatching {
                Class.forName("com.microsoft.playwright.impl.driver.Driver", false, loader)
                    .protectionDomain?.codeSource?.location?.toURI()
            }.getOrNull() ?: continue
            val path = runCatching { Path.of(location) }.getOrNull() ?: continue
            if (Files.isRegularFile(path) && isDriverJar(path.fileName.toString())) return path
        }

        // 回退：与 driver-bundle 同目录时直接取 driver-<version>.jar（绝不匹配 driver-bundle）。
        val dir = driverBundle?.parent ?: return null
        return dir.toFile().listFiles()?.firstOrNull {
            it.isFile && isDriverJar(it.name)
        }?.toPath()
    }

    private fun isDriverJar(name: String): Boolean =
        name.startsWith("driver-") && !name.startsWith("driver-bundle") && name.endsWith(".jar")

    /** 构造最终安装命令（纯函数，测试使用）。 */
    public fun buildInvocation(node: File, cli: File, options: PlaywrightInstallOptions): PlaywrightInstallCommand {
        val argv = buildList {
            add(node.absolutePath)
            add(cli.absolutePath)
            add("install")
            add("chromium")
            options.shell.cliArg?.let(::add)
            if (withDeps(options)) add("--with-deps")
        }
        return PlaywrightInstallCommand(
            argv = argv,
            displayText = argv.joinToString(" ") { cmdQuote(it) },
            environment = environment(options)
        )
    }

    private fun validate(options: PlaywrightInstallOptions): PlaywrightInstallResult? {
        if (options.browsersPath.isBlank()) {
            return failure(PlaywrightErrorCodes.CONFIG_INVALID, "browsers_path 不能为空", emptyList(), null, false)
        }
        val host = options.downloadHost?.trim().orEmpty()
        if (host.isNotEmpty() && !host.startsWith("http://", true) && !host.startsWith("https://", true)) {
            return failure(
                PlaywrightErrorCodes.CONFIG_INVALID,
                "download_host 必须是 http(s) URL：$host",
                emptyList(), null, false
            )
        }
        return null
    }

    private fun environment(options: PlaywrightInstallOptions): Map<String, String> = buildMap {
        put("PLAYWRIGHT_BROWSERS_PATH", File(options.browsersPath).absolutePath)
        options.downloadHost?.trim()?.takeIf { it.isNotEmpty() }?.let { put("PLAYWRIGHT_DOWNLOAD_HOST", it) }
        // 显式安装必须允许下载；上层渲染进程才会设置 skip。
    }

    private fun withDeps(options: PlaywrightInstallOptions): Boolean =
        options.withDeps ?: !isWindows(options.osName) && !options.osName.lowercase().contains("mac")

    private fun findManagedBrowser(options: PlaywrightInstallOptions): String? =
        BrowserStrategy.findManagedBrowser(options.browsersPath, options.osName)

    private fun javaExecutable(options: PlaywrightInstallOptions): String {
        val home = options.javaHome?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getProperty("java.home").orEmpty()
        val exe = if (isWindows(options.osName)) "java.exe" else "java"
        return File(File(home, "bin"), exe).absolutePath
    }

    private fun isWindows(osName: String): Boolean = osName.lowercase().contains("win")

    private fun failure(
        code: String,
        detail: String,
        output: List<String>,
        exitCode: Int?,
        timedOut: Boolean,
        /** 失败时也要给出「手动命令」，否则命令层只能打印一行空的「手动命令:」。 */
        command: String = ""
    ): PlaywrightInstallResult = PlaywrightInstallResult(
        success = false,
        code = code,
        message = buildString {
            append('[').append(code).append("] ").append(detail)
            if (output.isNotEmpty()) append("\n输出（尾部）：\n").append(output.joinToString("\n"))
        },
        command = command,
        outputTail = output,
        exitCode = exitCode,
        timedOut = timedOut,
        installedBrowserPath = null
    )

    private fun extractDriver(bundle: Path, platformDir: String, tempDir: Path): Path {
        val prefix = "driver/$platformDir/"
        val destination = tempDir.resolve(platformDir)
        Files.createDirectories(destination)
        var found = false
        ZipFile(bundle.toFile()).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory || !entry.name.startsWith(prefix)) continue
                val relative = entry.name.removePrefix(prefix)
                if (relative.isBlank()) continue
                val target = destination.resolve(relative).normalize()
                if (!target.startsWith(destination)) {
                    throw IOException("driver-bundle 含非法路径：${entry.name}")
                }
                Files.createDirectories(target.parent)
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
                found = true
            }
        }
        if (!found) throw IOException("driver-bundle 中没有 $prefix")
        return destination
    }

    private class OutputCollector(process: Process, private val limit: Int) {
        private val tail = ArrayDeque<String>()
        private val thread = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        synchronized(tail) {
                            tail.addLast(line)
                            while (tail.size > limit) tail.removeFirst()
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "PlumBot-Playwright-Installer-Output"
            start()
        }

        fun join(millis: Long) {
            runCatching { thread.join(millis) }
        }

        fun snapshot(): List<String> = synchronized(tail) { tail.toList() }
    }

    private fun decisionLabel(decision: BrowserDecision): String = when (decision) {
        is BrowserDecision.Executable -> "${decision.source}: ${decision.path}"
        is BrowserDecision.Channel -> "channel: ${decision.channel}"
        is BrowserDecision.InvalidConfig -> "配置非法: ${decision.detail}"
        is BrowserDecision.Missing -> "无可用浏览器"
    }

    private companion object {
        const val DRIVER_JAR_CLASS = "com.microsoft.playwright.impl.driver.jar.DriverJar"

        fun cmdQuote(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""

        fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
    }
}
