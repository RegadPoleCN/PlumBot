// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.engine.playwright.startup.EngineStartup
import me.regadpole.plumbot.render.theme.ThemeManager
import net.kyori.adventure.text.Component

/**
 * 引擎关停配套的回归护栏：TCCL 必须无条件恢复（原值为 null 时不得滞留插件加载器）；
 * 渲染泳道线程池在 closeDispatcher 后必须真正归还。
 */
class EngineLifecycleTcclTest {

    private class TestConfig : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext : PlatformContext {
        override val config = TestConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = Files.createTempDirectory("plumbot-tccl-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("TCCL 测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    @Test
    fun `ensureStarted 启动失败后把 TCCL 无条件恢复为原值（包括 null）`() {
        val ctx = TestContext()
        val themeManager = ThemeManager(ctx, Files.createTempDirectory("plumbot-tccl-web").toFile())
        val failureMapper = FailureMapper { themeManager }
        val pool = PagePool(ctx)
        val lifecycle = EngineLifecycle(
            ctx, null, pool, failureMapper,
            EngineStartup(ctx, failureMapper),
            { themeManager }
        )

        val observed = AtomicReference<ClassLoader?>(Thread.currentThread().contextClassLoader)
        val finished = CountDownLatch(1)
        val worker = Thread {
            try {
                try {
                    lifecycle.ensureStarted()
                } catch (expected: Throwable) {
                    // 测试类路径没有 playwright：启动必然快速失败，这里只关心 TCCL 恢复。
                }
                observed.set(Thread.currentThread().contextClassLoader)
            } finally {
                finished.countDown()
            }
        }
        worker.contextClassLoader = null
        worker.start()
        assertTrue(finished.await(5, TimeUnit.SECONDS), "启动失败路径必须快速返回")
        assertTrue(observed.get() == null, "TCCL 原值为 null 时不得把插件类加载器滞留在工作线程上，实际 " + observed.get())
    }
}

class PagePoolCloseTest {

    private class TestConfig : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext : PlatformContext {
        override val config = TestConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = Files.createTempDirectory("plumbot-pool-close-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("页面池测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    @Test
    fun `closeDispatcher 之后渲染泳道拒绝新任务`() {
        val pool = PagePool(TestContext())
        runBlocking { withContext(pool.dispatcher) { } }
        pool.closeDispatcher()
        // 协程调度器把底层线程池的 RejectedExecutionException 包装成 CancellationException 抛出。
        val error = assertFailsWith<CancellationException> {
            runBlocking { withContext(pool.dispatcher) { } }
        }
        assertTrue(error.message?.contains("rejected") == true, error.message ?: "无错误消息")
    }
}