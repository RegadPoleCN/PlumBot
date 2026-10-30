// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 门户关闭**不得阻塞调用线程**（性能回归护栏）。
 *
 * 背景：`/plumbot reload` 会从服务端主线程调用 `shutdown()`，旧实现在锁内同步执行
 * `PlaywrightImageRenderer.shutdown()` → `Browser.close()` / `PlaywrightImpl.close()`，
 * 其中 `Process.waitFor()` 等 node 驱动退出 —— spark profile 实测占主线程 0.88%，
 * 单 tick MSPT 最高 1817ms。这里用一个"关闭很慢"的假引擎把该行为钉死：
 * 关闭动作必须离开调用线程，且最终确实执行（不是丢掉了事）。
 */
class RenderShutdownTest {

    private class SlowShutdownRenderer(private val closeDelayMs: Long) : ImageRenderer {
        override val type: String = "fake-slow-close"
        override val isSupported: Boolean = true
        val shutdownCount = AtomicInteger()

        override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? = null

        override fun shutdown() {
            Thread.sleep(closeDelayMs)
            shutdownCount.incrementAndGet()
        }
    }

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
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-shutdown-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(
                initialDelay: kotlin.time.Duration,
                period: kotlin.time.Duration,
                task: () -> Unit
            ) = PlatformTaskHandle { true }

            override fun runRepeatingAsync(
                initialDelay: kotlin.time.Duration,
                period: kotlin.time.Duration,
                task: () -> Unit
            ) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("关闭测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    @Test
    fun `门户关闭立即返回而不等待引擎 close`() {
        val slow = SlowShutdownRenderer(closeDelayMs = 600)
        val portal = ImageRenderManager(TestContext()).also { it.registerEngineForTest(slow) }

        val startedAt = System.nanoTime()
        portal.shutdown()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue(
            elapsedMs < 300,
            "shutdown() 必须不阻塞调用线程，实际耗时 ${elapsedMs}ms（引擎 close 需要 600ms）"
        )
        // 关闭动作没有丢：回收线程最终会执行到它
        assertTrue(RenderTeardown.awaitIdle(5_000), "回收队列未在超时前排空")
        assertEquals(1, slow.shutdownCount.get(), "假引擎应恰好被关闭一次")
    }

    @Test
    fun `shutdownAndAwait 会等到引擎真正关闭`() {
        val slow = SlowShutdownRenderer(closeDelayMs = 300)
        val portal = ImageRenderManager(TestContext()).also { it.registerEngineForTest(slow) }

        val startedAt = System.nanoTime()
        val drained = portal.shutdownAndAwait(5_000)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue(drained, "5s 预算内必须排空")
        assertEquals(1, slow.shutdownCount.get(), "返回 true 就代表引擎已经关闭完毕")
        assertTrue(elapsedMs >= 300, "必须真的等，实际只用了 ${elapsedMs}ms（引擎 close 需要 300ms）")
    }

    @Test
    fun `shutdownAndAwait 超时返回 false 而不是抛异常`() {
        val slow = SlowShutdownRenderer(closeDelayMs = 2_000)
        val portal = ImageRenderManager(TestContext()).also { it.registerEngineForTest(slow) }

        // 停服路径不能因为驱动卡住就把整个 JVM 关停挂死：超时只允许返回 false。
        assertTrue(!portal.shutdownAndAwait(150), "150ms 预算内不可能关完 2s 的引擎")
        assertTrue(RenderTeardown.awaitIdle(10_000), "回收线程最终仍须跑完，避免测试泄漏线程")
    }

    @Test
    fun `重复关闭是幂等的且不重复关闭引擎`() {
        val slow = SlowShutdownRenderer(closeDelayMs = 0)
        val portal = ImageRenderManager(TestContext()).also { it.registerEngineForTest(slow) }

        portal.shutdown()
        portal.shutdown()
        assertTrue(RenderTeardown.awaitIdle(5_000), "回收队列未在超时前排空")
        assertEquals(1, slow.shutdownCount.get(), "第二次 shutdown() 不应再次关闭已摘下的引擎")
    }

    @Test
    fun `单个回收任务失败不影响后续任务`() {
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())
        val ran = AtomicInteger()

        RenderTeardown.submit({ t -> failures += t }) { throw IllegalStateException("boom") }
        RenderTeardown.submit({ t -> failures += t }) { ran.incrementAndGet() }

        assertTrue(RenderTeardown.awaitIdle(5_000), "回收队列未在超时前排空")
        assertEquals(1, failures.size, "失败应被上报一次而不是抛出")
        assertEquals("boom", failures.first().message)
        assertEquals(1, ran.get(), "前一个任务失败不得吞掉后继任务")
    }

    @Test
    fun `回收任务串行执行且保持提交顺序`() {
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val threads = Collections.synchronizedSet(mutableSetOf<String>())
        (1..20).forEach { index ->
            RenderTeardown.submit({ }) {
                order += index
                threads += Thread.currentThread().name
            }
        }
        assertTrue(RenderTeardown.awaitIdle(5_000), "回收队列未在超时前排空")
        assertEquals((1..20).toList(), order.toList(), "回收必须串行且按提交顺序执行")
        assertEquals(setOf("PlumBot-Render-Reaper"), threads.toSet(), "回收任务必须在回收线程上执行")
    }
}
