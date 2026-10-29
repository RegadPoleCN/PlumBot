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

import java.io.File

/**
 * 浏览器获取决策（**不存在**"隐式自动下载"）。
 *
 * 顺序：
 * 1. 显式 `browser_channel`（品牌通道，交给 Playwright 通过注册表/系统安装位置解析）；
 * 2. `custom_chrome_path`（存在则直接作为 `executablePath`）；
 * 3. 系统已安装的 Chrome/Edge/Chromium（**一律** `executablePath`，不猜测 channel）；
 * 4. 插件托管目录 `browsers_path` 中由安装器落盘的 Chromium；
 * 5. 以上都没有 → [Missing]，由渲染器抛出 `PW_BROWSER_MISSING` 并给出手动安装命令。
 *
 * 第 3/4 级**绝不**映射成 `channel`：Linux 的 `/usr/bin/chromium` 不是 Playwright 的
 * `chrome` 通道，旧实现这样映射会在 launch 阶段直接失败。
 *
 * 本对象只做纯决策与路径枚举，下载/启动由 Installer 与 Renderer 负责，便于单测。
 */
internal sealed interface BrowserDecision {
    val note: String?

    /** 使用明确的可执行文件路径。 */
    data class Executable(
        val path: String,
        /** custom_chrome_path / system / managed，用于日志与 doctor。 */
        val source: String,
        override val note: String? = null
    ) : BrowserDecision

    /** 使用 Playwright 支持的品牌通道（只允许 chrome/msedge 家族）。 */
    data class Channel(val channel: String, override val note: String? = null) : BrowserDecision

    /** 配置冲突或非法值：必须 fail-fast，不能悄悄退回其他浏览器。 */
    data class InvalidConfig(val detail: String) : BrowserDecision {
        override val note: String? = null
    }

    /** 没有可用浏览器：必须给出安装命令，而不是让 Playwright 隐式下载。 */
    data class Missing(val detail: String) : BrowserDecision {
        override val note: String? = null
    }
}

internal object BrowserStrategy {

    /** Playwright 1.49 支持的品牌通道（不含 chromium —— 它不是 channel）。 */
    val SUPPORTED_CHANNELS: Set<String> = linkedSetOf(
        "chrome", "chrome-beta", "chrome-dev", "chrome-canary",
        "msedge", "msedge-beta", "msedge-dev", "msedge-canary"
    )

    /** 按上述优先级决策；[pathExists] 可注入，生产实现同时检查可执行位。 */
    fun decide(
        customPath: String?,
        explicitChannel: String?,
        detectedPath: String?,
        managedPath: String?,
        pathExists: (String) -> Boolean = ::defaultPathExists
    ): BrowserDecision {
        val custom = customPath?.trim().orEmpty()
        val channel = explicitChannel?.trim().orEmpty()

        if (custom.isNotEmpty() && channel.isNotEmpty()) {
            return BrowserDecision.InvalidConfig(
                "custom_chrome_path 与 browser_channel 不能同时配置；请二选一（当前：$custom / $channel）"
            )
        }

        if (channel.isNotEmpty()) {
            if (channel.lowercase() !in SUPPORTED_CHANNELS) {
                return BrowserDecision.InvalidConfig(
                    "browser_channel='$channel' 不受支持；可选值：" + SUPPORTED_CHANNELS.joinToString(", ")
                )
            }
            return BrowserDecision.Channel(channel.lowercase(), "使用配置指定的浏览器通道：$channel")
        }

        if (custom.isNotEmpty()) {
            return if (pathExists(custom)) {
                BrowserDecision.Executable(custom, "custom_chrome_path")
            } else {
                // 配置了却不存在属于配置错误；继续探测会让服主误以为配置生效。
                BrowserDecision.InvalidConfig(
                    "custom_chrome_path 指向的文件不存在或不可执行：$custom"
                )
            }
        }

        val detected = detectedPath?.trim().orEmpty()
        if (detected.isNotEmpty() && pathExists(detected)) {
            return BrowserDecision.Executable(
                detected,
                "system",
                "已探测到系统浏览器：$detected（作为 executablePath 使用，不映射 channel）"
            )
        }

        val managed = managedPath?.trim().orEmpty()
        if (managed.isNotEmpty() && pathExists(managed)) {
            return BrowserDecision.Executable(
                managed,
                "managed",
                "使用安装器管理的 Playwright 浏览器：$managed"
            )
        }

        return BrowserDecision.Missing(
            "未找到可用的 Chromium 系浏览器（custom_chrome_path/browser_channel 未配置，" +
                "系统 Chrome/Edge/Chromium 与 browsers_path 托管目录均未命中）"
        )
    }

    /**
     * 系统浏览器候选路径（按 OS 区分）。Windows 同时覆盖 Program Files 与 LocalAppData，
     * 避免只装用户级 Chrome 的机器被误判为"没有浏览器"。
     */
    fun systemBrowserCandidates(
        osName: String,
        env: Map<String, String> = System.getenv()
    ): List<String> {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> buildList {
                val local = env["LOCALAPPDATA"]?.trim().orEmpty()
                if (local.isNotEmpty()) {
                    add(local + "\\Google\\Chrome\\Application\\chrome.exe")
                    add(local + "\\Chromium\\Application\\chrome.exe")
                    add(local + "\\Microsoft\\Edge\\Application\\msedge.exe")
                }
                val programFiles = env["ProgramFiles"]?.trim().orEmpty()
                if (programFiles.isNotEmpty()) {
                    add(programFiles + "\\Google\\Chrome\\Application\\chrome.exe")
                    add(programFiles + "\\Microsoft\\Edge\\Application\\msedge.exe")
                }
                val programFilesX86 = env["ProgramFiles(x86)"]?.trim().orEmpty()
                if (programFilesX86.isNotEmpty()) {
                    add(programFilesX86 + "\\Google\\Chrome\\Application\\chrome.exe")
                    add(programFilesX86 + "\\Microsoft\\Edge\\Application\\msedge.exe")
                }
                // 环境变量缺失时的固定兜底（只用于诊断/旧环境）。
                add("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe")
                add("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe")
                add("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe")
                add("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe")
            }

            os.contains("mac") -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"
            )

            else -> listOf(
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/snap/bin/chromium",
                "/opt/google/chrome/chrome",
                "/usr/lib/chromium/chromium",
                "/usr/bin/microsoft-edge",
                "/usr/bin/microsoft-edge-stable"
            )
        }
    }

    /** 探测系统浏览器可执行文件；未安装返回 null。 */
    fun detectSystemBrowserPath(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: Map<String, String> = System.getenv(),
        pathExists: (String) -> Boolean = ::defaultPathExists
    ): String? = systemBrowserCandidates(osName, env).firstOrNull(pathExists)

    /**
     * 扫描 `browsers_path` 下由安装器写入的 Chromium。
     *
     * Playwright 的目录布局随平台不同（chrome-win/chrome.exe、chrome-linux/chrome、
     * chrome-mac…/Chromium.app/...，以及 headless shell 变体），这里全部枚举；
     * 版本目录按修订号倒序，优先使用最新安装。
     */
    fun findManagedBrowser(
        browsersPath: String,
        osName: String = System.getProperty("os.name").orEmpty(),
        pathExists: (String) -> Boolean = ::defaultPathExists
    ): String? {
        val root = File(browsersPath)
        if (!root.isDirectory) return null

        val versionDirs = root.listFiles { file ->
            file.isDirectory && (file.name.startsWith("chromium-") || file.name.startsWith("chromium_headless_shell-"))
        }?.sortedWith(
            compareByDescending<File> { revisionOf(it.name) }
                .thenByDescending { it.name.startsWith("chromium-") } // full 优先于 shell
        ).orEmpty()

        for (dir in versionDirs) {
            for (relative in managedRelativePaths(osName)) {
                val candidate = File(dir, relative)
                if (pathExists(candidate.absolutePath)) return candidate.absolutePath
            }
        }
        return null
    }

    private fun managedRelativePaths(osName: String): List<String> {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> listOf(
                "chrome-win/chrome.exe",
                "chrome-win64/chrome.exe",
                "chrome-win32/chrome.exe",
                "chrome-headless-shell-win64/chrome-headless-shell.exe",
                "chrome-headless-shell-win32/chrome-headless-shell.exe"
            )

            os.contains("mac") -> listOf(
                "chrome-mac/Chromium.app/Contents/MacOS/Chromium",
                "chrome-mac-arm64/Chromium.app/Contents/MacOS/Chromium",
                "chrome-mac-x64/Chromium.app/Contents/MacOS/Chromium",
                "chrome-headless-shell-mac-arm64/chrome-headless-shell",
                "chrome-headless-shell-mac-x64/chrome-headless-shell"
            )

            else -> listOf(
                "chrome-linux/chrome",
                "chrome-linux64/chrome",
                "chrome-headless-shell-linux64/chrome-headless-shell",
                "chrome-headless-shell-linux/chrome-headless-shell"
            )
        }
    }

    private fun revisionOf(name: String): Int =
        Regex("-(\\d+)$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /**
     * 解析持久化浏览器目录。
     *
     * 默认落 `dataDirectory/playwright-browsers` —— 避免写进
     * `~/.cache/ms-playwright` 或 `%USERPROFILE%\AppData\Local\ms-playwright`（不可控、不可卸载、可能爆盘）。
     */
    fun resolveBrowsersPath(configValue: String?, pluginFolder: String): String {
        val configured = configValue?.trim().orEmpty()
        if (configured.isNotEmpty()) {
            return configured.replace("%plugin_folder%", pluginFolder).replace('\\', '/')
        }
        return "$pluginFolder/playwright-browsers".replace('\\', '/')
    }

    /** 解析浏览器下载镜像；为空表示使用官方 CDN。 */
    fun resolveDownloadHost(configValue: String?): String? =
        configValue?.trim()?.takeIf { it.isNotEmpty() }

    /** 生产路径判定：文件存在，且非 Windows 时具备可执行位。 */
    private fun defaultPathExists(path: String): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        return System.getProperty("os.name").orEmpty().lowercase().contains("win") || file.canExecute()
    }
}
