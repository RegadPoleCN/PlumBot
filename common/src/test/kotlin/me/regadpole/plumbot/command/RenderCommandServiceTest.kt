// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.command

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.PlaywrightDiagnosis
import me.regadpole.plumbot.render.PlaywrightErrorCodes
import me.regadpole.plumbot.render.PlaywrightInstallOptions
import me.regadpole.plumbot.render.PlaywrightInstallResult
import me.regadpole.plumbot.render.PlaywrightShell
import me.regadpole.plumbot.testing.FakePlatformConfig
import net.kyori.adventure.text.Component
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `/plumbot render doctor|install` 的非阻塞契约测试。
 *
 * 调度器替身**只入队不执行**，用来证明：诊断与安装的真实工作被投递到异步泳道（命令线程
 * 当场不阻塞），结果回调一律切回同步泳道（命令发送者只在主线程安全）。
 *
 * 测试不联网、不起子进程：测试 JVM 没有 Playwright 依赖，安装器必然走「缺依赖」分支，
 * 正好验证缺件时仍给出结构化、可操作的结果。
 */
class RenderCommandServiceTest {

    /** 入队式调度器：用泳道顺序证明「只调度、不就地执行」。 */
    private class RecordingScheduler : PlatformScheduler {
        val syncTasks = ArrayDeque<() -> Unit>()
        val asyncTasks = ArrayDeque<() -> Unit>()

        /** 非空时模拟异步线程池已关闭：runAsync 把异常直接抛给调用线程。 */
        var asyncFailure: Throwable? = null

        override fun runSync(task: () -> Unit): PlatformTaskHandle {
            syncTasks += task
            return PlatformTaskHandle { true }
        }

        override fun runAsync(task: () -> Unit): PlatformTaskHandle {
            asyncFailure?.let { throw it }
            asyncTasks += task
            return PlatformTaskHandle { true }
        }

        override fun runLater(delay: kotlin.time.Duration, task: () -> Unit): PlatformTaskHandle = handle()

        override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit): PlatformTaskHandle = handle()

        override fun runRepeating(
            initialDelay: kotlin.time.Duration,
            period: kotlin.time.Duration,
            task: () -> Unit,
        ): PlatformTaskHandle = handle()

        override fun runRepeatingAsync(
            initialDelay: kotlin.time.Duration,
            period: kotlin.time.Duration,
            task: () -> Unit,
        ): PlatformTaskHandle = handle()

        private fun handle(): PlatformTaskHandle = PlatformTaskHandle { true }

        /** 先排空异步泳道（其内部会再投递同步回调），再排空同步泳道。 */
        fun drainAll() {
            while (asyncTasks.isNotEmpty()) asyncTasks.removeFirst().invoke()
            while (syncTasks.isNotEmpty()) syncTasks.removeFirst().invoke()
        }
    }

    private class ServiceContext(
        private val fakeConfig: FakePlatformConfig = FakePlatformConfig(),
        override val scheduler: PlatformScheduler = RecordingScheduler(),
    ) : PlatformContext {

        /** 临时目录代替 TODO()：安装目录解析会真实读取 dataDirectory。 */
        private val tempDir: Path = java.nio.file.Files.createTempDirectory("plumbot-render-cmd")
            .also { it.toFile().deleteOnExit() }

        override val config: PlatformConfig get() = fakeConfig
        override val datasource: PlatformConfig get() = fakeConfig
        override val dataDirectory: Path get() = tempDir
        override val playerService: PlatformPlayerService get() = TODO("测试未使用玩家服务")
        override val platformType: PlatformType get() = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    @Test
    fun `异步诊断只调度且回调切回同步泳道`() {
        val scheduler = RecordingScheduler()
        val service = RenderCommandService(ServiceContext(scheduler = scheduler))
        val diagnoses = mutableListOf<PlaywrightDiagnosis>()
        val errors = mutableListOf<Throwable>()

        service.diagnoseAsync(onDone = { diagnoses += it }, onError = { errors += it })

        assertTrue(diagnoses.isEmpty(), "异步任务未执行前不得回调结果")
        assertTrue(errors.isEmpty(), "异步任务未执行前不得回调错误")
        assertTrue(scheduler.asyncTasks.isNotEmpty(), "诊断必须投递到异步泳道")
        assertTrue(scheduler.syncTasks.isEmpty(), "诊断不得占用同步泳道")

        scheduler.drainAll()

        assertEquals(1, diagnoses.size, "诊断结果必须恰好回调一次")
        assertTrue(errors.isEmpty(), "正常路径不应触发 onError")
        assertTrue(scheduler.syncTasks.isEmpty(), "回调后同步泳道应已排空")
        // 测试 JVM 无 playwright 依赖：诊断必须给出「缺依赖」，而不是崩溃或谎报就绪。
        assertFalse(diagnoses.first().ready, "缺少 Playwright 依赖时不得报告就绪")
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, diagnoses.first().code)
        assertTrue(diagnoses.first().summary.isNotBlank())
        assertTrue(diagnoses.first().manualCommand.isNotBlank(), "失败必须附带手动安装命令")
    }

    @Test
    fun `调度失败时诊断走 onError 而不是抛给命令线程`() {
        val scheduler = RecordingScheduler().apply { asyncFailure = IllegalStateException("pool shut down") }
        val service = RenderCommandService(ServiceContext(scheduler = scheduler))
        val diagnoses = mutableListOf<PlaywrightDiagnosis>()
        val errors = mutableListOf<Throwable>()

        service.diagnoseAsync(onDone = { diagnoses += it }, onError = { errors += it })

        assertTrue(diagnoses.isEmpty())
        assertEquals(1, errors.size, "调度异常必须转成 onError")
        assertEquals("pool shut down", errors.first().message)
    }

    @Test
    fun `异步安装先报进度后报结果且都在同步泳道`() {
        val scheduler = RecordingScheduler()
        val service = RenderCommandService(ServiceContext(scheduler = scheduler))
        val progress = mutableListOf<String>()
        val results = mutableListOf<PlaywrightInstallResult>()

        service.installAsync(force = false, onProgress = { progress += it }, onDone = { results += it })

        assertTrue(progress.isEmpty(), "安装线程未启动前不得回调")
        assertTrue(results.isEmpty())
        assertTrue(scheduler.asyncTasks.isNotEmpty(), "安装必须投递到异步泳道")

        scheduler.drainAll()

        assertEquals(1, progress.size)
        assertEquals(1, results.size)
        val result = results.first()
        assertFalse(result.success, "测试 JVM 缺 Playwright 依赖，安装不应谎报成功")
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, result.code)
        assertTrue(result.message.isNotBlank())
        assertFalse(result.timedOut)
    }

    @Test
    fun `调度失败时安装仍给出可操作结果`() {
        val scheduler = RecordingScheduler().apply { asyncFailure = IllegalStateException("pool shut down") }
        val service = RenderCommandService(ServiceContext(scheduler = scheduler))
        val progress = mutableListOf<String>()
        val results = mutableListOf<PlaywrightInstallResult>()

        service.installAsync(force = true, onProgress = { progress += it }, onDone = { results += it })

        assertEquals(1, progress.size, "调度失败也必须告知用户尝试了什么")
        assertEquals(1, results.size, "调度失败不得静默吞掉")
        val result = results.first()
        assertFalse(result.success)
        assertEquals(PlaywrightErrorCodes.INSTALL_FAILED, result.code)
        assertTrue(result.message.contains("无法调度安装任务"), "错误信息要指向调度失败")
        assertTrue(result.command.isNotBlank(), "失败要带上可复制的手动命令")
        assertTrue(scheduler.asyncTasks.isEmpty() && scheduler.syncTasks.isEmpty())
    }

    @Test
    fun `安装选项映射配置并按内置默认兜底`() {
        val config = FakePlatformConfig(
            mapOf(
                "render.playwright.browsers_path" to "%plugin_folder%\\My Browsers",
                "render.playwright.download_host" to "  ",
                "render.playwright.install_timeout_seconds" to 0,
                "render.playwright.install_shell" to "bogus",
                "render.playwright.custom_chrome_path" to "C:/chrome/chrome.exe",
                "render.playwright.browser_channel" to "chrome"
            )
        )
        val context = ServiceContext(fakeConfig = config)
        val options = RenderCommandService(context).options()

        val pluginFolder = context.dataDirectory.toFile().absolutePath.replace("\\", "/")
        assertEquals("$pluginFolder/My Browsers", options.browsersPath)
        assertTrue(options.browsersPath.startsWith(pluginFolder), "%plugin_folder% 必须展开")
        assertEquals(null, options.downloadHost, "空白镜像地址应回落官方 CDN")
        assertEquals(PlaywrightInstallOptions.DEFAULT_TIMEOUT_SECONDS, options.timeoutSeconds)
        assertEquals(PlaywrightShell.NO_SHELL, options.shell, "非法取值必须显式回落默认，不得猜测")
        assertEquals("C:/chrome/chrome.exe", options.customChromePath)
        assertEquals("chrome", options.browserChannel)
    }

    @Test
    fun `诊断展示项覆盖目录镜像 shell 与超时`() {
        val config = FakePlatformConfig(
            mapOf(
                "render.playwright.browsers_path" to "D:/pw-browsers",
                "render.playwright.download_host" to "https://cdn.npmmirror.com/binaries/playwright",
                "render.playwright.install_timeout_seconds" to 42,
                "render.playwright.install_shell" to "full"
            )
        )
        val lines = RenderCommandService(ServiceContext(fakeConfig = config)).describeOptions()

        assertEquals(4, lines.size)
        assertTrue(lines.any { it.startsWith("browsers_path=") && it.contains("pw-browsers") }, lines.joinToString())
        assertTrue(lines.any { it.contains("https://cdn.npmmirror.com/binaries/playwright") }, lines.joinToString())
        assertTrue(lines.any { it == "install_shell=full" }, lines.joinToString())
        assertTrue(lines.any { it == "timeout=42s" }, lines.joinToString())
    }

    @Test
    fun `同步诊断与异步诊断结果一致`() {
        val config = FakePlatformConfig(mapOf("render.playwright.browsers_path" to "D:/pw-browsers"))
        val sync = RenderCommandService(ServiceContext(fakeConfig = config)).diagnose()

        val scheduler = RecordingScheduler()
        val async = mutableListOf<PlaywrightDiagnosis>()
        RenderCommandService(ServiceContext(fakeConfig = config, scheduler = scheduler))
            .diagnoseAsync(onDone = { async += it }, onError = { })
        scheduler.drainAll()

        assertEquals(sync.ready, async.first().ready)
        assertEquals(sync.code, async.first().code)
        assertEquals(sync.summary, async.first().summary)
    }

    @Test
    fun `运行库装载失败时诊断与安装都带上真实原因`() {
        // Leaf 1.21.11 的现场：Libby 从未被触发，doctor 只报「未找到」，服主无从判断是网络、仓库还是镜像。
        val scheduler = RecordingScheduler()
        val service = RenderCommandService(ServiceContext(scheduler = scheduler))
        service.libraryLoader = { "driver-bundle: 404 Not Found" }

        val diagnoses = mutableListOf<PlaywrightDiagnosis>()
        service.diagnoseAsync(onDone = { diagnoses += it }, onError = { })
        scheduler.drainAll()

        val diagnosis = diagnoses.single()
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, diagnosis.code)
        assertTrue(
            diagnosis.details.any { it.startsWith("library=") && it.contains("driver-bundle") },
            "诊断详情必须带装载失败原因：${diagnosis.details}",
        )
        assertTrue(diagnosis.summary.contains("运行库装载失败原因"), diagnosis.summary)

        val results = mutableListOf<PlaywrightInstallResult>()
        service.installAsync(force = true, onProgress = { }, onDone = { results += it })
        scheduler.drainAll()

        val result = results.single()
        assertFalse(result.success, "运行库缺失时安装不得进入 Chromium 下载")
        assertEquals(PlaywrightErrorCodes.DEPENDENCY_MISSING, result.code)
        assertTrue(result.message.contains("driver-bundle"), result.message)
        assertTrue(result.command.isNotBlank(), "失败要带上可复制的手动命令")
    }
}
