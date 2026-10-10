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
 * 浏览器获取决策（基线 §6.2 的**三级链**）。
 *
 * ```
 * ① config.custom_chrome_path 非空且可执行 → 直接用
 *     （路径无效：告警并落 ②，不直接失败）
 * ② 未指定 → 探测系统 Chromium 系（显式 browser_channel 优先于自动探测）
 * ③ 均未命中 → 交给 Playwright 自动下载
 * ```
 *
 * 本对象是**纯决策逻辑**，不触碰文件系统以外的副作用，便于单元测试。
 */
internal sealed interface BrowserDecision {
    val note: String?

    /** ① 使用配置指定的可执行文件。 */
    data class Executable(val path: String, override val note: String? = null) : BrowserDecision

    /** ② 复用系统已安装的 Chromium 系浏览器。 */
    data class Channel(val channel: String, override val note: String? = null) : BrowserDecision

    /** ③ 由 Playwright 自动下载（无自定义路径时"自动回退到下载"）。 */
    data class AutoDownload(override val note: String? = null) : BrowserDecision
}

internal object BrowserStrategy {

    /**
     * @param pathExists 注入的文件存在性判定（便于测试；生产传 [File.isFile]）
     */
    fun decide(
        customPath: String?,
        explicitChannel: String?,
        detectedChannel: String?,
        pathExists: (String) -> Boolean = { File(it).isFile }
    ): BrowserDecision {
        val custom = customPath?.trim().orEmpty()
        if (custom.isNotEmpty()) {
            return if (pathExists(custom)) {
                BrowserDecision.Executable(custom)
            } else {
                val fallback = resolveChannel(explicitChannel, detectedChannel)
                when (fallback) {
                    is BrowserDecision.Channel -> fallback.copy(
                        note = "custom_chrome_path 无效（$custom），已改用系统浏览器"
                    )
                    else -> BrowserDecision.AutoDownload("custom_chrome_path 无效（$custom），已回退为自动下载")
                }
            }
        }
        return resolveChannel(explicitChannel, detectedChannel)
    }

    private fun resolveChannel(explicitChannel: String?, detectedChannel: String?): BrowserDecision {
        val explicit = explicitChannel?.trim().orEmpty()
        if (explicit.isNotEmpty()) return BrowserDecision.Channel(explicit)

        val detected = detectedChannel?.trim().orEmpty()
        if (detected.isNotEmpty()) {
            return BrowserDecision.Channel(detected, "已探测到系统浏览器：$detected")
        }
        return BrowserDecision.AutoDownload()
    }

    /**
     * 浏览器二进制的候选路径（按 OS 区分），返回 `路径 → Playwright channel`。
     */
    fun candidatePaths(osName: String): List<Pair<String, String>> {
        val os = osName.lowercase()
        return when {
            os.contains("win") -> listOf(
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe" to "chrome",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe" to "chrome",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe" to "msedge",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe" to "msedge"
            )
            os.contains("mac") -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" to "chrome",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge" to "msedge"
            )
            else -> listOf(
                "/usr/bin/google-chrome" to "chrome",
                "/usr/bin/google-chrome-stable" to "chrome",
                "/usr/bin/chromium" to "chrome",
                "/usr/bin/chromium-browser" to "chrome",
                "/usr/bin/microsoft-edge" to "msedge"
            )
        }
    }

    /** 探测系统浏览器；未安装返回 null。 */
    fun detectSystemBrowser(osName: String = System.getProperty("os.name").orEmpty()): String? =
        candidatePaths(osName).firstOrNull { (path, _) -> File(path).isFile }?.second

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
}
