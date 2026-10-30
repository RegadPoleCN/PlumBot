// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.command

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.PlaywrightDiagnosis
import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightInstallResult
import me.regadpole.plumbot.render.PlaywrightInstaller
import me.regadpole.plumbot.render.PlaywrightShell
import me.regadpole.plumbot.render.RenderProvider
import java.io.File

/**
 * `/plumbot render doctor|install` 的业务实现。
 *
 * 放在 common 而不是各平台命令类里：三端（Bukkit/Velocity/Hytale）的配置键、浏览器目录与
 * 手动命令必须完全一致 —— 同一个报错在三端给出不同命令，比没有命令更糟。
 *
 * @param context 读取 `render.playwright.*` 并复用平台数据目录
 */
class RenderCommandService(private val context: PlatformContext) {

    private val installer = PlaywrightInstaller(context.javaClass.classLoader)

    /** 已就「原始类型不是字符串」告警过的键（doctor 会被反复执行）。 */
    private val typeWarnings = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private companion object {
        /** 文档里一律是字符串的 `render.playwright.*` 键；类型错位即视为异常。 */
        val STRING_KEYS = listOf(
            "browsers_path",
            "download_host",
            "install_shell",
            "custom_chrome_path",
            "browser_channel"
        )
    }

    /**
     * 安装/诊断的执行接缝。
     *
     * [PlaywrightInstaller] 不是 open 类，`diagnoseAsync` / `installAsync` 的**线程契约**
     * （不在调用线程执行、回调切回主线程、安装器抛异常时落成 INSTALL_FAILED）若不抽接缝，
     * 就只能靠真实下载 Chromium 验证。生产路径永不设置 [installerOps]。
     */
    internal interface InstallerOps {
        fun diagnose(options: PlaywrightInstallOptions): PlaywrightDiagnosis
        fun install(options: PlaywrightInstallOptions, force: Boolean): PlaywrightInstallResult
        fun manualCommand(options: PlaywrightInstallOptions): String
    }

    /** 测试注入点；null = 真实 [installer]。 */
    internal var installerOps: InstallerOps? = null

    /**
     * 运行库装载接缝（测试注入）：返回 null 表示 Playwright 三件套已就绪，否则是可展示的失败原因。
     *
     * 生产路径走 [RenderProvider] 的门户实现。诊断/安装**必须先装载再检查**，否则 Libby 尚未
     * 被触发时会误报 `PW_DEPENDENCY_MISSING`（Leaf 1.21.11 的实际故障）。
     */
    internal var libraryLoader: (() -> String?)? = null

    private fun ensurePlaywrightLibrary(): String? {
        libraryLoader?.let { return it() }
        val portal = RenderProvider.getOrNull()
            ?: return "渲染门户未初始化（RenderProvider 为空；请先 /plumbot reload 或重启服务端）"
        return portal.ensurePlaywrightLibrary()
    }

    private fun ops(): InstallerOps = installerOps ?: defaultOps

    private val defaultOps = object : InstallerOps {
        override fun diagnose(options: PlaywrightInstallOptions): PlaywrightDiagnosis = installer.diagnose(options)

        override fun install(options: PlaywrightInstallOptions, force: Boolean): PlaywrightInstallResult =
            installer.install(options, force)

        override fun manualCommand(options: PlaywrightInstallOptions): String = installer.manualCommand(options)
    }

    /**
     * 读取一个**期望是字符串**的 `render.playwright.*` 键，原始类型不是字符串时告警。
     *
     * 配置树被就地改写后（`getBoolean` 把 `render.engine: playwright` 写回 `Boolean false`），
     * 这里读到的仍是字符串 `"false"`，看起来完全合法 —— 只有原始类型能戳破。每个键只提示
     * 一次：doctor 会被反复执行，不该刷屏。
     */
    private fun playwrightString(key: String): String? {
        val value = context.config.getString("render", "playwright", key)
        val type = context.config.rawTypeOf("render", "playwright", key)
        if (type != null && type != "String" && type != "missing" && type != "null" &&
            typeWarnings.add(key)
        ) {
            context.log(
                LogLevel.WARN,
                "[PlumBot] 配置 render.playwright.$key 的原始类型是 $type（读到 '$value'），期望字符串；" +
                    "这通常是配置树被某个读取方就地改写所致（历史 bug：布尔读取会把值写回 false）。" +
                    "请把该项改成字符串并重启，再用 /plumbot doctor render 确认类型标注消失。"
            )
        }
        return value
    }

    /** 原始类型异常（非字符串）的键，给 doctor 输出追加 `[raw=Boolean]` 标注。 */
    private fun rawTypeNote(key: String): String {
        val type = context.config.rawTypeOf("render", "playwright", key) ?: return ""
        if (type == "String" || type == "missing" || type == "null") return ""
        return "[raw=$type]"
    }

    /** 与渲染驱动的浏览器目录、镜像与超时配置保持一致（同一组 `render.playwright.*` 键）。 */
    fun options(): PlaywrightInstallOptions {
        val config = context.config
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val browsersPath = playwrightString("browsers_path")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.replace("%plugin_folder%", pluginFolder)
            ?.replace('\\', '/')
            ?: "$pluginFolder/playwright-browsers".replace('\\', '/')
        val timeout = config.getInteger("render", "playwright", "install_timeout_seconds")
            .let { if (it > 0) it else PlaywrightInstallOptions.DEFAULT_TIMEOUT_SECONDS }
        return PlaywrightInstallOptions(
            browsersPath = browsersPath,
            downloadHost = playwrightString("download_host")
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
            timeoutSeconds = timeout,
            shell = PlaywrightShell.parse(playwrightString("install_shell"))
                ?: PlaywrightShell.NO_SHELL,
            customChromePath = playwrightString("custom_chrome_path"),
            browserChannel = playwrightString("browser_channel")
        )
    }

    /**
     * 诊断（**会触发** Playwright 运行库装载，可能下载约 190 MB）。
     *
     * 装载是「诊断能看到 jar」的前提：不装载就查 `driver-bundle`/`playwright.jar`，只会得到一条
     * 虚假的依赖缺失。该调用阻塞，命令层必须用 [diagnoseAsync]。
     */
    fun diagnose(): PlaywrightDiagnosis {
        val libraryFailure = ensurePlaywrightLibrary()
        return diagnoseWithLibraryFailure(libraryFailure)
    }

    private fun diagnoseWithLibraryFailure(libraryFailure: String?): PlaywrightDiagnosis {
        val diagnosis = ops().diagnose(options())
        if (libraryFailure == null) return diagnosis
        return diagnosis.copy(
            details = diagnosis.details + "library=$libraryFailure",
            summary = if (diagnosis.code == PlaywrightErrorCodes.DEPENDENCY_MISSING) {
                diagnosis.summary + "；运行库装载失败原因：$libraryFailure"
            } else {
                diagnosis.summary
            }
        )
    }

    /**
     * 异步诊断；完成/失败回调都切回平台主线程。
     *
     * 先在同一异步泳道里装载运行库（首次会下载 driver-bundle，约 190 MB）再执行只读检查；
     * 两者都不在主线程，门户自带单飞与冷却，不会重复触发下载。
     */
    fun diagnoseAsync(onDone: (PlaywrightDiagnosis) -> Unit, onError: (Throwable) -> Unit) {
        try {
            context.scheduler.runAsync {
                context.log(LogLevel.INFO, "[PlumBot] 正在准备 Playwright 运行库（driver-bundle 约 190 MB，首次需下载）…")
                runCatching { diagnoseWithLibraryFailure(ensurePlaywrightLibrary()) }
                    .onSuccess { diagnosis -> context.scheduler.runSync { onDone(diagnosis) } }
                    .onFailure { error -> context.scheduler.runSync { onError(error) } }
            }
        } catch (t: Throwable) {
            onError(t)
        }
    }

    /**
     * 异步安装 Chromium；完成回调被切回平台主线程（命令发送者只在主线程安全）。
     *
     * @param force true 时即使已检测到浏览器也重新下载
     */
    fun installAsync(force: Boolean, onProgress: (String) -> Unit, onDone: (PlaywrightInstallResult) -> Unit) {
        val startMessage = "已开始准备 Playwright 运行库并安装 Chromium（driver-bundle 约 190 MB，首次需下载）；过程日志写入控制台，完成后在这里汇报结果。";
        try {
            context.scheduler.runAsync {
                context.log(LogLevel.INFO, "[PlumBot] 正在准备 Playwright 运行库（driver-bundle 约 190 MB，首次需下载）…")
                val libraryFailure = ensurePlaywrightLibrary()
                val result = if (libraryFailure != null) {
                    installFailure(
                        "Playwright 运行库未就绪：$libraryFailure",
                        PlaywrightErrorCodes.DEPENDENCY_MISSING
                    )
                } else {
                    try {
                        ops().install(options(), force)
                    } catch (t: Throwable) {
                        installFailure("安装器异常：${t.message ?: t.javaClass.simpleName}")
                    }
                }
                context.scheduler.runSync { onDone(result) }
            }
            context.scheduler.runSync { onProgress(startMessage) }
        } catch (t: Throwable) {
            // 调度本身失败（线程池已关闭等）必须仍然给出可操作结果，不能把异常抛给命令执行线程。
            onProgress(startMessage);
            onDone(installFailure("无法调度安装任务：${t.message ?: t.javaClass.simpleName}"))
        }
    }

    /** 统一的安装失败载荷（含手动命令），供运行库缺失、异常与调度失败共用。 */
    private fun installFailure(
        detail: String,
        code: String = PlaywrightErrorCodes.INSTALL_FAILED
    ): PlaywrightInstallResult = PlaywrightInstallResult(
        success = false,
        code = code,
        message = detail,
        command = runCatching { ops().manualCommand(options()) }.getOrDefault("<无法构造手动命令>"),
        outputTail = emptyList(),
        exitCode = null,
        timedOut = false,
        installedBrowserPath = null
    )

    /** 手动安装命令（doctor 与失败消息共用）。 */
    fun manualCommand(): String = installer.manualCommand(options())

    /** 诊断用的一个人类可读目录名，便于确认装到了哪里。 */
    fun browsersPathDisplay(): String = options().browsersPath

    /** 安装命令参数逐项展示（诊断输出用）。 */
    fun describeOptions(): List<String> = options().let { options ->
        val lines = mutableListOf(
            "browsers_path=${File(options.browsersPath).absolutePath}${rawTypeNote("browsers_path")}",
            "download_host=${options.downloadHost?.takeIf { it.isNotBlank() } ?: "官方 CDN"}" +
                rawTypeNote("download_host"),
            "install_shell=${options.shell.configValue}${rawTypeNote("install_shell")}",
            "timeout=${options.timeoutSeconds}s"
        )
        // 协议字段自检（前端与插件各自独立演进，字段漂移需要可诊断）。
        RenderProvider.getOrNull()?.describeProtocolFields()?.let { lines += it }
        // 正常配置下是 4 行参数 + 一行协议摘要；只有原始类型错位时才多出定位信息。
        val suspicious = STRING_KEYS.mapNotNull { key ->
            val type = context.config.rawTypeOf("render", "playwright", key)
            if (type == null || type == "String" || type == "missing" || type == "null") null
            else "$key=$type"
        }
        if (suspicious.isNotEmpty()) {
            lines += "类型异常：${suspicious.joinToString(", ")}（期望字符串；疑为配置树被就地改写）"
        }
        lines
    }
}