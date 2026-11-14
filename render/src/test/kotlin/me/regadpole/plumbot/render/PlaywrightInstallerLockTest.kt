// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 跨进程安装锁的回归护栏：占用立即失败（不排队），锁文件不可用降级不阻断。 */
class PlaywrightInstallerLockTest {

    private fun options(browsersPath: String) = PlaywrightInstallOptions(
        browsersPath = browsersPath,
        downloadHost = "https://cdn.npmmirror.com/binaries/playwright",
        shell = PlaywrightShell.NO_SHELL,
        withDeps = null,
        osName = "Linux"
    )

    @Test
    fun `锁被占用时 install 立即失败且不启动安装进程`() {
        val dir = Files.createTempDirectory("plumbot-install-lock-busy")
        val lockFile = dir.resolve(PlaywrightInstaller.LOCK_FILE_NAME)
        val holder = PlaywrightInstaller()
        val channel = holder.installLockOpener(lockFile)
        assertTrue(channel != null, "首个持锁方必须能拿到锁")
        try {
            val busy = PlaywrightInstaller().install(options(dir.toString()), force = true)
            assertTrue(!busy.success, "锁被占用时不得继续安装")
            assertEquals(PlaywrightErrorCodes.INSTALL_FAILED, busy.code)
            assertTrue(busy.message.contains("另一个安装正在执行"), busy.message)
        } finally {
            runCatching { channel?.close() }
        }
        // 释放后重试：不再报"正在安装"，而是继续走安装流程（测试类路径缺 driver-bundle → 依赖缺失）。
        val after = PlaywrightInstaller().install(options(dir.toString()), force = true)
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, after.code, after.message)
        runCatching { dir.toFile().deleteRecursively() }
    }

    @Test
    fun `锁文件不可用时退化为仅本进程互斥而不阻断安装`() {
        val dir = Files.createTempDirectory("plumbot-install-lock-degrade")
        val logs = mutableListOf<String>()
        val installer = PlaywrightInstaller(log = { logs += it })
        installer.installLockOpener = { throw IOException("权限不足") }
        try {
            val result = installer.install(options(dir.toString()), force = true)
            assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, result.code, result.message)
            assertTrue(logs.any { it.contains("退化为仅本进程互斥") }, logs.toString())
        } finally {
            runCatching { dir.toFile().deleteRecursively() }
        }
    }
}