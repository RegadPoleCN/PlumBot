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
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaywrightInstallerTest {

    private fun options(
        browsersPath: String = "C:\\srv\\plugins\\PlumBot\\playwright-browsers",
        downloadHost: String? = "https://cdn.npmmirror.com/binaries/playwright",
        shell: PlaywrightShell = PlaywrightShell.NO_SHELL,
        withDeps: Boolean? = null,
        osName: String = "Linux"
    ) = PlaywrightInstallOptions(
        browsersPath = browsersPath,
        downloadHost = downloadHost,
        shell = shell,
        withDeps = withDeps,
        osName = osName
    )

    @Test
    fun `install_shell 解析空值为默认 no-shell 且非法值返回 null`() {
        assertEquals(PlaywrightShell.NO_SHELL, PlaywrightShell.parse(null))
        assertEquals(PlaywrightShell.NO_SHELL, PlaywrightShell.parse("  "))
        assertEquals(PlaywrightShell.NO_SHELL, PlaywrightShell.parse("NO-SHELL"))
        assertEquals(PlaywrightShell.ONLY_SHELL, PlaywrightShell.parse("only-shell"))
        assertEquals(PlaywrightShell.FULL, PlaywrightShell.parse("full"))
        assertNull(PlaywrightShell.parse("chrome"))
    }

    @Test
    fun `Linux 默认安装完整 Chromium 并追加 with-deps`() {
        val node = File("/tmp/node")
        val cli = File("/tmp/package/cli.js")
        val command = PlaywrightInstaller().buildInvocation(node, cli, options())
        assertEquals(
            listOf(node.absolutePath, cli.absolutePath, "install", "chromium", "--no-shell", "--with-deps"),
            command.argv
        )
        assertEquals(File("C:\\srv\\plugins\\PlumBot\\playwright-browsers").absolutePath, command.environment["PLAYWRIGHT_BROWSERS_PATH"])
        assertEquals("https://cdn.npmmirror.com/binaries/playwright", command.environment["PLAYWRIGHT_DOWNLOAD_HOST"])
        assertFalse(command.environment.containsKey("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"))
    }

    @Test
    fun `Windows 不加 with-deps 且 only-shell 传对应参数`() {
        val command = PlaywrightInstaller().buildInvocation(
            File("C:\\node.exe"),
            File("C:\\package\\cli.js"),
            options(shell = PlaywrightShell.ONLY_SHELL, osName = "Windows 11")
        )
        assertEquals(
            listOf("C:\\node.exe", "C:\\package\\cli.js", "install", "chromium", "--only-shell"),
            command.argv
        )
    }

    @Test
    fun `withDeps 显式 false 覆盖 Linux 默认值`() {
        val command = PlaywrightInstaller().buildInvocation(File("/tmp/node"), File("/tmp/cli.js"), options(withDeps = false))
        assertFalse(command.argv.contains("--with-deps"))
    }

    @Test
    fun `manualCommand 包含三个 jar 与 browsers_path 环境变量及 shell 参数`() {
        val command = PlaywrightInstaller().manualCommand(options())
        assertTrue(command.contains("com.microsoft.playwright.CLI"), command)
        assertTrue(command.contains("install"), command)
        assertTrue(command.contains("chromium"), command)
        assertTrue(command.contains("--no-shell"), command)
        assertTrue(command.contains("PLAYWRIGHT_BROWSERS_PATH"), command)
        assertTrue(command.contains("PLAYWRIGHT_DOWNLOAD_HOST"), command)
        // CLI 必须同时具备 API、Driver 接口和 node/cli.js 三个 jar；缺 driver.jar 会 NoClassDefFoundError。
        assertTrue(command.contains("playwright-1.49.0.jar"), command)
        assertTrue(command.contains("driver-1.49.0.jar"), command)
        assertTrue(command.contains("driver-bundle-1.49.0.jar"), command)
    }

    @Test
    fun `findDriverJar 不会把 driver-bundle 误当成 driver`() {
        val root = createTempDirectory("plumbot-pw-jars-")
        try {
            val bundle = root.resolve("driver-bundle-1.49.0.jar").also { it.writeText("stub") }
            val driver = root.resolve("driver-1.49.0.jar").also { it.writeText("stub") }
            root.resolve("playwright-1.49.0.jar").writeText("stub")
            val found = PlaywrightInstaller().findDriverJar(bundle)
            assertEquals(driver.toAbsolutePath().toString(), found?.toAbsolutePath().toString())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `browsers_path 为空时 install 立即返回配置错误`() {
        val result = PlaywrightInstaller().install(options(browsersPath = "  "), force = true)
        assertFalse(result.success)
        assertEquals(PlaywrightErrorCodes.CONFIG_INVALID, result.code)
        assertTrue(result.message.contains("browsers_path"), result.message)
    }

    @Test
    fun `download_host 非 http 时 install 立即返回配置错误`() {
        val result = PlaywrightInstaller().install(options(downloadHost = "ftp://mirror/pw"), force = true)
        assertFalse(result.success)
        assertEquals(PlaywrightErrorCodes.CONFIG_INVALID, result.code)
        assertTrue(result.message.contains("download_host"), result.message)
    }

    @Test
    fun `已有托管 Chromium 时 install 幂等成功且不启动进程`() {
        val root = createTempDirectory("plumbot-pw-managed-")
        try {
            val chrome = root.resolve("chromium-1148/chrome-linux/chrome")
            chrome.parent.createDirectories()
            chrome.writeText("stub")
            chrome.toFile().setExecutable(true)

            val result = PlaywrightInstaller().install(
                options(browsersPath = root.toAbsolutePath().toString(), osName = "Linux"),
                force = false
            )
            assertTrue(result.success, result.message)
            assertEquals(chrome.toAbsolutePath().toString(), result.installedBrowserPath)
            assertNull(result.code)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `缺少 driver-bundle 时 diagnose 报依赖缺失而不是浏览器缺失`() {
        val diagnosis = PlaywrightInstaller().diagnose(options())
        assertFalse(diagnosis.ready)
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, diagnosis.code)
        assertTrue(diagnosis.summary.contains("运行库"), diagnosis.summary)
        assertTrue(diagnosis.manualCommand.contains("com.microsoft.playwright.CLI"), diagnosis.manualCommand)
    }

    @Test
    fun `diagnose 不再重复打印命令层已输出的目录与镜像`() {
        // browsers_path/download_host/install_shell 已在 RenderCommandService.describeOptions()
        // 打印过，这里再打一次就是重复行。
        val details = PlaywrightInstaller().diagnose(options()).details
        listOf("browsers_path=", "download_host=", "install_shell=").forEach { prefix ->
            assertTrue(details.none { it.startsWith(prefix) }, "doctor 详情不得重复打印 $prefix：$details")
        }
        val decision = details.single { it.startsWith("decision=") }
        assertTrue(decision.contains("运行库"), "缺运行库时 decision 必须标注不可用，避免误以为 Edge 已被采用：$decision")
    }

    @Test
    fun `缺少 driver-bundle 时 install 仍给出手动命令`() {
        val result = PlaywrightInstaller().install(options(), force = true)
        assertFalse(result.success)
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, result.code)
        assertTrue(result.message.contains("190"), result.message)
        // 失败路径也必须带可复制的手动命令。
        assertTrue(result.command.contains("com.microsoft.playwright.CLI"), result.command)
    }

    @Test
    fun `PlaywrightRenderException 的 message 带 code stage route hint 与命令`() {
        val exception = PlaywrightRenderException(
            code = PlaywrightErrorCodes.BROWSER_MISSING,
            stage = "browser",
            detail = "未找到浏览器",
            hint = "安装 Chromium",
            command = "java -cp ... com.microsoft.playwright.CLI install chromium",
            route = "message"
        )
        val message = exception.message.orEmpty()
        assertTrue(message.contains("[PW_BROWSER_MISSING]"), message)
        assertTrue(message.contains("stage=browser"), message)
        assertTrue(message.contains("route=message"), message)
        assertTrue(message.contains("处理建议"), message)
        assertTrue(message.contains("手动命令"), message)
    }
}
