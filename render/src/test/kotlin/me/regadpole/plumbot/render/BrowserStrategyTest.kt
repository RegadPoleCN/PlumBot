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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 浏览器三级获取链的决策测试（基线 §6.2）。
 *
 * 旧实现只做了"非空就 setExecutablePath"，既不校验路径存在性，也完全没有
 * "探测系统浏览器"与"自动下载兜底"两级。
 */
class BrowserStrategyTest {

    private val exists = { path: String -> path == "/valid/chrome" }

    @Test
    fun `配置指定且有效时优先使用指定可执行文件`() {
        val decision = BrowserStrategy.decide(
            customPath = "/valid/chrome",
            explicitChannel = "msedge",
            detectedChannel = "chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.Executable>(decision)
        assertEquals("/valid/chrome", decision.path)
    }

    @Test
    fun `配置路径无效时降级到探测到的系统浏览器而不是直接失败`() {
        val decision = BrowserStrategy.decide(
            customPath = "/missing/chrome",
            explicitChannel = null,
            detectedChannel = "chrome",
            pathExists = exists
        )
        assertIs<BrowserDecision.Channel>(decision)
        assertEquals("chrome", decision.channel)
        assertTrue(decision.note?.contains("无效") == true, "应给出降级说明：${decision.note}")
    }

    @Test
    fun `配置路径无效且无系统浏览器时回退为自动下载`() {
        val decision = BrowserStrategy.decide(
            customPath = "/missing/chrome",
            explicitChannel = null,
            detectedChannel = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.AutoDownload>(decision)
        assertTrue(decision.note?.contains("自动下载") == true)
    }

    @Test
    fun `未配置路径且无系统浏览器时自动下载`() {
        val decision = BrowserStrategy.decide(
            customPath = null,
            explicitChannel = null,
            detectedChannel = null,
            pathExists = exists
        )
        assertIs<BrowserDecision.AutoDownload>(decision)
        assertNull(decision.note)
    }

    @Test
    fun `显式 channel 优先于自动探测`() {
        val decision = BrowserStrategy.decide(
            customPath = "  ",
            explicitChannel = "chrome-beta",
            detectedChannel = "msedge",
            pathExists = exists
        )
        assertIs<BrowserDecision.Channel>(decision)
        assertEquals("chrome-beta", decision.channel)
    }

    @Test
    fun `空白配置被视为未配置`() {
        val decision = BrowserStrategy.decide(
            customPath = "",
            explicitChannel = "   ",
            detectedChannel = "",
            pathExists = exists
        )
        assertIs<BrowserDecision.AutoDownload>(decision)
    }

    @Test
    fun `候选路径按操作系统区分且 channel 取值合法`() {
        val windows = BrowserStrategy.candidatePaths("Windows 11")
        val linux = BrowserStrategy.candidatePaths("Linux")
        val mac = BrowserStrategy.candidatePaths("Mac OS X")

        assertTrue(windows.any { it.first.endsWith("chrome.exe") })
        assertTrue(linux.any { it.first.startsWith("/usr/bin/") })
        assertTrue(mac.any { it.first.contains("Google Chrome.app") })

        val validChannels = setOf("chrome", "msedge")
        (windows + linux + mac).forEach { (_, channel) ->
            assertTrue(channel in validChannels, "channel 必须是 Playwright 支持的品牌通道：$channel")
        }
    }

    @Test
    fun `浏览器目录默认落在插件数据目录内`() {
        val resolved = BrowserStrategy.resolveBrowsersPath(null, "C:\\srv\\plugins\\PlumBot")
        assertTrue(resolved.startsWith("C:/srv/plugins/PlumBot"), "应位于数据目录内：$resolved")
        assertTrue(resolved.endsWith("playwright-browsers"))
        assertTrue(!resolved.contains('\\'), "应为 URL/路径通用的正斜杠形式：$resolved")
    }

    @Test
    fun `浏览器目录支持插件目录占位符`() {
        val resolved = BrowserStrategy.resolveBrowsersPath("%plugin_folder%/pw", "/srv/plum")
        assertEquals("/srv/plum/pw", resolved)
    }

    @Test
    fun `下载镜像为空时不设置`() {
        assertNull(BrowserStrategy.resolveDownloadHost(null))
        assertNull(BrowserStrategy.resolveDownloadHost("   "))
        assertEquals("https://mirror.example/pw", BrowserStrategy.resolveDownloadHost(" https://mirror.example/pw "))
    }
}
