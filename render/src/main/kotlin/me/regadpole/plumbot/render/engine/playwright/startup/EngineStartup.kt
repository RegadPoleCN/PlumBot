// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright.startup

import java.io.File
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightInstaller
import me.regadpole.plumbot.render.PlaywrightShell
import me.regadpole.plumbot.render.engine.playwright.FailureMapper
import me.regadpole.plumbot.render.install.BrowserDecision
import me.regadpole.plumbot.render.install.BrowserStrategy

/**
 * Playwright 引擎的启动配置解析：安装选项、浏览器来源决策、进程环境变量、设备像素比。
 *
 * 与 [EngineLifecycle] 分开是因为这些逻辑**不涉及引擎状态**：它们只读配置、只做策略选择，
 * 出错时也只构造配置类失败。抽出来后引擎生命周期只剩"活没活、什么时候死"。
 *
 * 纯策略（浏览器决策、环境变量、决策描述）放在 [Companion] 里做成无状态函数，
 * 便于单测与复用；需要读配置/日志的部分（安装选项、scale_factor、手动安装命令）才带实例状态。
 */
internal class EngineStartup(
    private val context: PlatformContext,
    private val failureMapper: FailureMapper
) {

    /** 安装器只用于生成手动命令，不在这里执行下载。 */
    private val installer: PlaywrightInstaller by lazy {
        PlaywrightInstaller(EngineStartup::class.java.classLoader) { message ->
            context.log(LogLevel.INFO, "[PlumBot-Render] " + message)
        }
    }

    /**
     * 解析安装选项。
     *
     * 浏览器二进制固定在插件数据目录；安装 shell 的非法值直接算配置失败，
     * 不静默回落到默认值（否则用户以为设置了隔离却实际没有）。
     */
    fun installOptions(): PlaywrightInstallOptions {
        val pluginFolder = context.dataDirectory.toFile().absolutePath
        val browsersPath = BrowserStrategy.resolveBrowsersPath(
            context.config.getString("render", "playwright", "browsers_path"),
            pluginFolder
        )
        val configuredTimeout = context.config.getInteger("render", "playwright", "install_timeout_seconds")
        val timeoutSeconds = if (configuredTimeout > 0) configuredTimeout else DEFAULT_INSTALL_TIMEOUT_SECONDS
        val rawShell = context.config.getString("render", "playwright", "install_shell")
        val shell = PlaywrightShell.parse(rawShell)
            ?: failureMapper.configFailure(
                "render.playwright.install_shell='" + (rawShell ?: "") + "' 非法；可选值：no-shell / only-shell / full"
            )

        return PlaywrightInstallOptions(
            browsersPath = File(browsersPath).absolutePath,
            downloadHost = BrowserStrategy.resolveDownloadHost(
                context.config.getString("render", "playwright", "download_host")
            ),
            timeoutSeconds = timeoutSeconds,
            shell = shell,
            customChromePath = context.config.getString("render", "playwright", "custom_chrome_path"),
            browserChannel = context.config.getString("render", "playwright", "browser_channel"),
            osName = System.getProperty("os.name").orEmpty()
        )
    }

    /**
     * 浏览器上下文的设备像素比（device_scale_factor），在创建上下文时生效。
     * 取自 render.playwright.scale_factor（1.0 ~ 4.0，支持 1.5 这类小数）。
     */
    fun globalScaleFactor(): Double {
        val configured = context.config.getDouble("render", "playwright", "scale_factor")
        if (configured.isFinite() && configured in MIN_SCALE_FACTOR..MAX_SCALE_FACTOR) return configured
        if (configured.isFinite() && configured > 0.0) {
            context.log(
                LogLevel.WARN,
                "[PlumBot-Render] scale_factor=" + configured + " 超出 " + MIN_SCALE_FACTOR + "~" + MAX_SCALE_FACTOR + " 区间，已回落 " + DEFAULT_SCALE_FACTOR
            )
        }
        return DEFAULT_SCALE_FACTOR
    }

    /** 手动安装命令（由安装器生成）；失败时只是不给命令，不改变主失败原因。 */
    fun manualInstallCommand(options: PlaywrightInstallOptions): String? =
        runCatching { installer.manualCommand(options) }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /**
         * 选择顺序（无自动下载）：browser_channel → custom_chrome_path → 系统浏览器 → 安装器管理的 playwright-browsers。
         *
         * 纯函数：只依赖传入的 [options]，不读配置、不写日志。
         */
        fun resolveBrowserDecision(options: PlaywrightInstallOptions): BrowserDecision {
            val detectedPath = BrowserStrategy.detectSystemBrowserPath(options.osName)
            val managedPath = BrowserStrategy.findManagedBrowser(options.browsersPath, options.osName)
            return BrowserStrategy.decide(
                customPath = options.customChromePath,
                explicitChannel = options.browserChannel,
                detectedPath = detectedPath,
                managedPath = managedPath
            )
        }

        /**
         * Playwright 进程环境变量。
         *
         * - PLAYWRIGHT_BROWSERS_PATH: 浏览器二进制固定在插件数据目录，便于卸载与诊断；
         * - PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1: 关闭 create() 内的无条件 install（首帧卡死的根因）；
         * - PLAYWRIGHT_DOWNLOAD_HOST: 只给显式安装器/手动命令使用，渲染进程不下载。
         *
         * 纯函数：只依赖传入的 [options]。
         */
        fun playwrightEnvironment(options: PlaywrightInstallOptions): Map<String, String> {
            val env = mutableMapOf(
                "PLAYWRIGHT_BROWSERS_PATH" to options.browsersPath,
                "PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "1"
            )
            options.downloadHost?.let { env["PLAYWRIGHT_DOWNLOAD_HOST"] = it }
            return env
        }

        /** 浏览器来源的可读描述（只用于启动日志）。纯函数。 */
        fun describeDecision(decision: BrowserDecision): String = when (decision) {
            is BrowserDecision.Executable -> decision.path + "（" + decision.source + "）"
            is BrowserDecision.Channel -> "channel=" + decision.channel
            is BrowserDecision.InvalidConfig -> "配置非法"
            is BrowserDecision.Missing -> "未找到浏览器"
        }

        /** 安装超时缺省值。 */
        const val DEFAULT_INSTALL_TIMEOUT_SECONDS = 600

        const val DEFAULT_SCALE_FACTOR = 1.5
        const val MIN_SCALE_FACTOR = 1.0
        const val MAX_SCALE_FACTOR = 4.0
    }
}
