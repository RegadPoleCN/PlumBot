// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.render.install.BrowserDecision
import me.regadpole.plumbot.render.install.BrowserStrategy

/**
 * 浏览器获取决策测试。
 *
 * AutoDownload 已不存在：第 3/4 级系统/托管浏览器一律映射 executablePath，
 * 配置错误与"没有浏览器"都必须显式失败，交给调用方报 PW_CONFIG_INVALID / PW_BROWSER_MISSING。
 */
class BrowserStrategyTest {

    private val exists = { path: String -> path == "/valid/chrome" }

    @Test
    fun `custom 与 channel 同时配置是配置错误`() {
        val decision = BrowserStrategy.decide(
            customPath = "/valid/chrome",
            explicitChannel = "msedge",
            detectedPath = null,
            managedPath = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.InvalidConfig>(decision)
        assertTrue(decision.detail.contains("不能同时配置"), decision.detail)
    }

    @Test
    fun `显式品牌 channel 优先于 custom 之外的所有探测`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = "Chrome-Beta",
            detectedPath = "/valid/chrome",
            managedPath = "/managed/chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.Channel>(decision)
        assertEquals("chrome-beta", decision.channel)
    }

    @Test
    fun `chromium 不是 Playwright channel 必须报配置错误`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = "chromium",
            detectedPath = null,
            managedPath = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.InvalidConfig>(decision)
        assertTrue(decision.detail.contains("不受支持"), decision.detail)
    }

    @Test
    fun `custom 路径存在时使用 executablePath`() {
        val decision = BrowserStrategy.decide(
            customPath = "/valid/chrome",
            explicitChannel = null,
            detectedPath = "/sys/chrome",
            managedPath = "/managed/chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.Executable>(decision)
        assertEquals("/valid/chrome", decision.path)
        assertEquals("custom_chrome_path", decision.source)
    }

    @Test
    fun `custom 路径不存在必须失败而不是继续探测`() {
        val decision = BrowserStrategy.decide(
            customPath = "/missing/chrome",
            explicitChannel = null,
            detectedPath = "/valid/chrome",
            managedPath = "/managed/chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.InvalidConfig>(decision)
        assertTrue(decision.detail.contains("不存在"), decision.detail)
    }

    @Test
    fun `系统浏览器命中时只作为 executablePath 不映射 channel`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = null,
            detectedPath = "/valid/chrome",
            managedPath = "/managed/chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.Executable>(decision)
        assertEquals("/valid/chrome", decision.path)
        assertEquals("system", decision.source)
        assertTrue(decision.note?.contains("executablePath") == true)
    }

    @Test
    fun `系统浏览器缺失时使用托管目录`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = null,
            detectedPath = null,
            managedPath = "/managed/chrome",
            pathExists = { it == "/managed/chrome" }
        )
        assertIs<BrowserDecision.Executable>(decision)
        assertEquals("managed", decision.source)
    }

    @Test
    fun `全部缺失时返回 Missing 而不是隐式下载`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = null,
            detectedPath = null,
            managedPath = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.Missing>(decision)
        assertTrue(decision.detail.contains("未找到"), decision.detail)
    }

    @Test
    fun `空白配置视为未配置`() {
        val decision = BrowserStrategy.decide(
            customPath = "  ",
            explicitChannel = "   ",
            detectedPath = null,
            managedPath = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.Missing>(decision)
    }

    @Test
    fun `Windows 候选覆盖 LocalAppData 与 Program Files 且不含 channel`() {
        val env = mapOf(
            "LOCALAPPDATA" to "C:\\Users\\me\\AppData\\Local",
            "ProgramFiles" to "D:\\Program Files",
            "ProgramFiles(x86)" to "D:\\Program Files (x86)"
        )
        val candidates = BrowserStrategy.systemBrowserCandidates("Windows 11", env)
        assertTrue(candidates.any { it.contains("AppData\\Local") && it.endsWith("chrome.exe") })
        assertTrue(candidates.any { it.startsWith("D:\\Program Files\\Microsoft\\Edge") })
        assertTrue(candidates.any { it.startsWith("D:\\Program Files (x86)\\Google\\Chrome") })
    }

    @Test
    fun `Linux 候选包含 chromium 且仍以路径形式返回`() {
        val candidates = BrowserStrategy.systemBrowserCandidates("Linux", emptyMap())
        assertTrue(candidates.contains("/usr/bin/chromium"))
        assertTrue(candidates.contains("/usr/bin/google-chrome"))
    }

    @Test
    fun `系统浏览器探测返回首个存在路径`() {
        val detected = BrowserStrategy.detectSystemBrowserPath(
            osName = "Linux",
            env = emptyMap(),
            pathExists = { it == "/usr/bin/chromium" }
        )
        assertEquals("/usr/bin/chromium", detected)
    }

    @Test
    fun `托管目录按修订号倒序，较新的 headless shell 也会被选中`() {
        val root = createTempDirectory("plumbot-pw-browsers-")
        try {
            val old = root.resolve("chromium-1100/chrome-win64/chrome.exe")
            val full = root.resolve("chromium-1148/chrome-win64/chrome.exe")
            val shell = root.resolve("chromium_headless_shell-1149/chrome-headless-shell-win64/chrome-headless-shell.exe")
            listOf(old, full, shell).forEach {
                it.parent.createDirectories()
                it.writeText("stub")
            }
            val found = BrowserStrategy.findManagedBrowser(
                root.toString(),
                "Windows 11",
                pathExists = { Files.isRegularFile(Path.of(it)) }
            )
            // 修订号是主序：升级到 1149 的 shell 安装后，旧的 1148 full 不应再被优先使用。
            assertEquals(shell.toAbsolutePath().toString(), found)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `同一修订号下完整 Chromium 优先于 headless shell`() {
        val root = createTempDirectory("plumbot-pw-browsers-")
        try {
            val full = root.resolve("chromium-1148/chrome-win64/chrome.exe")
            val shell = root.resolve("chromium_headless_shell-1148/chrome-headless-shell-win64/chrome-headless-shell.exe")
            listOf(full, shell).forEach {
                it.parent.createDirectories()
                it.writeText("stub")
            }
            val found = BrowserStrategy.findManagedBrowser(
                root.toString(),
                "Windows 11",
                pathExists = { Files.isRegularFile(Path.of(it)) }
            )
            assertEquals(full.toAbsolutePath().toString(), found)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `托管目录不存在时返回 null`() {
        assertNull(BrowserStrategy.findManagedBrowser("/definitely/missing/pw", "Linux"))
    }

    @Test
    fun `浏览器目录默认落在插件数据目录内`() {
        val resolved = BrowserStrategy.resolveBrowsersPath(null, "C:\\srv\\plugins\\PlumBot")
        assertEquals("C:/srv/plugins/PlumBot/playwright-browsers", resolved)
    }

    @Test
    fun `浏览器目录支持插件目录占位符且统一正斜杠`() {
        val resolved = BrowserStrategy.resolveBrowsersPath("%plugin_folder%/pw", "C:\\srv\\plum")
        assertEquals("C:/srv/plum/pw", resolved)
    }

    @Test
    fun `下载镜像为空时不设置`() {
        assertNull(BrowserStrategy.resolveDownloadHost(null))
        assertNull(BrowserStrategy.resolveDownloadHost("   "))
        assertEquals("https://mirror.example/pw", BrowserStrategy.resolveDownloadHost(" https://mirror.example/pw "))
    }
}
