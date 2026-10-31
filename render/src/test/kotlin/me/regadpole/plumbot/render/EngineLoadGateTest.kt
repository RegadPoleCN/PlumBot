// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.pipeline.ImageRenderManager
import net.kyori.adventure.text.Component

/**
 * **按需装载门**与「立即返回、异步装载」契约。
 *
 * 断言的是「**装载被调度**」这件事本身：本机没有 Playwright 类，"装载成功后能否出图"
 * 无法在此验证（需真机），但"门控是否会把装载路径短路掉"、"主线程是否被同步下载阻塞"
 * 完全可以验证。
 */
class EngineLoadGateTest {

    /** 替身装载器：记录调用次数与执行线程，可设定成功/失败/抛异常。 */
    private class FakeLoader(var result: Boolean = true, var throws: Boolean = false) : RenderLibraryLoader {
        var calls = 0
            private set
        val threads = mutableListOf<Thread>()

        override fun ensurePlaywright(): Boolean {
            calls++
            threads += Thread.currentThread()
            if (throws) throw IllegalStateException("仓库不可达")
            return result
        }
    }

    private class Config(values: Map<String, Any>) : PlatformConfig {
        private val values = values.toMutableMap()
        private fun k(path: Array<out String?>) = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[k(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[k(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[k(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[k(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    /**
     * 可控调度器：runAsync 只入队，测试线程手动 drain；
     * drain 用独立的 worker 线程执行，用来证明装载**不在调用线程**上发生。
     */
    private class RecordingScheduler : PlatformScheduler {
        private val asyncTasks = ArrayDeque<() -> Unit>()
        val asyncThreads = mutableListOf<Thread>()

        val pendingAsync: Int get() = asyncTasks.size

        override fun runSync(task: () -> Unit): PlatformTaskHandle {
            task()
            return PlatformTaskHandle { true }
        }

        override fun runAsync(task: () -> Unit): PlatformTaskHandle {
            asyncTasks.addLast(task)
            return PlatformTaskHandle { true }
        }

        override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }

        fun drainOnWorker() {
            while (asyncTasks.isNotEmpty()) {
                val task = asyncTasks.removeFirst()
                val worker = Thread({
                    asyncThreads += Thread.currentThread()
                    task()
                }, "plumbot-test-render-async").also { it.isDaemon = true }
                worker.start()
                worker.join()
            }
        }
    }

    private class Context(values: Map<String, Any>, val recordingScheduler: RecordingScheduler = RecordingScheduler()) : PlatformContext {
        override val config: PlatformConfig = Config(values)
        val logs = mutableListOf<String>()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-gate").also { it.toFile().deleteOnExit() }
        override val scheduler: PlatformScheduler get() = recordingScheduler
        override val playerService: PlatformPlayerService get() = TODO("本用例不使用玩家服务")
        override val platformType: PlatformType get() = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) { logs += "$level $message" }
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun portal(values: Map<String, Any>, loader: RenderLibraryLoader?): Pair<ImageRenderManager, Context> {
        val context = Context(values)
        return ImageRenderManager(context, libraryLoader = loader, runtimeWebDir = File(context.dataDirectory.toFile(), "web")) to context
    }

    private fun portal(engine: String, loader: RenderLibraryLoader?): Pair<ImageRenderManager, Context> =
        // text 路由也有独立门控（render.outputs.text），全局引擎用例需显式启用，
        // 否则所有路由都被门控关闭，预热根本不会进入引擎解析。
        portal(mapOf("render.engine" to engine, "render.outputs.text" to true), loader)

    @Test
    fun `warm up schedules the on demand load and returns before it runs`() {
        val loader = FakeLoader(result = true)
        val (manager, context) = portal("playwright", loader)

        val capable = manager.warmUpEngines()

        assertTrue(capable, "配置了 playwright 时，首帧应先放行进渲染路径")
        assertEquals(0, loader.calls, "warmUpEngines 必须立即返回，绝不能在调用线程同步装载")
        assertEquals(1, context.recordingScheduler.pendingAsync, "必须把装载调度到平台异步线程")

        context.recordingScheduler.drainOnWorker()

        assertEquals(1, loader.calls, "engine=playwright 时必须触发按需装载，否则首帧会退化成无渲染结果")
        assertTrue(
            loader.threads.none { it === Thread.currentThread() },
            "装载必须在异步线程执行，实际线程：${loader.threads}"
        )
    }

    @Test
    fun `repeated warm ups coalesce into a single asynchronous load`() {
        val loader = FakeLoader(result = true)
        val (manager, context) = portal("playwright", loader)

        manager.warmUpEngines()
        manager.warmUpEngines()
        manager.warmUpEngines()

        assertEquals(1, context.recordingScheduler.pendingAsync, "并发/重复预热只能调度一次装载")
        assertEquals(0, loader.calls)

        context.recordingScheduler.drainOnWorker()

        assertEquals(1, loader.calls, "同一时间只允许一个装载任务")
        assertEquals(0, context.recordingScheduler.pendingAsync)
    }

    @Test
    fun `a failed load is reported and cooled down instead of retried on every call`() {
        val loader = FakeLoader(result = false)
        val (manager, context) = portal("playwright", loader)

        manager.warmUpEngines()
        context.recordingScheduler.drainOnWorker()
        manager.warmUpEngines()
        manager.warmUpEngines()

        assertEquals(1, loader.calls, "失败后必须进入冷却，不得每次调用都重试下载")
        assertEquals(0, context.recordingScheduler.pendingAsync, "冷却期内不得再调度装载")
        assertTrue(
            context.logs.any { it.contains("冷却") },
            "装载失败必须给出可诊断的告警（离线/仓库不可达）：${context.logs}"
        )
    }

    @Test
    fun `a throwing loader does not propagate into the caller or the scheduler thread`() {
        val loader = FakeLoader(throws = true)
        val (manager, context) = portal("playwright", loader)

        manager.warmUpEngines() // 不得抛出
        context.recordingScheduler.drainOnWorker() // 异步线程也不得抛出

        assertEquals(1, loader.calls)
        assertTrue(context.logs.any { it.contains("按需装载异常") }, "异常要被记录：${context.logs}")
    }

    @Test
    fun `route level playwright override schedules warm up under global awt`() {
        val loader = FakeLoader(result = true)
        val (manager, context) = portal(
            mapOf(
                "render.engine" to "awt",
                "render.views.status.engine" to "playwright",
                "render.outputs.status" to true
            ),
            loader
        )

        manager.warmUpEngines()

        assertEquals(
            1,
            context.recordingScheduler.pendingAsync,
            "预热必须扫描实际启用的路由，不能只看 STATUS 的全局引擎"
        )
        context.recordingScheduler.drainOnWorker()
        assertEquals(1, loader.calls)
    }

    @Test
    fun `disabled route override does not trigger warm up`() {
        val loader = FakeLoader(result = true)
        val (manager, context) = portal(
            mapOf(
                "render.engine" to "awt",
                "render.views.status.engine" to "playwright",
                "render.outputs.status" to false
            ),
            loader
        )

        manager.warmUpEngines()

        assertEquals(0, context.recordingScheduler.pendingAsync, "关闭的功能不应触发 Playwright 下载")
        assertEquals(0, loader.calls)
    }

    @Test
    fun `the awt engine never triggers a playwright download`() = runBlocking {
        val loader = FakeLoader(result = true)
        val (manager, context) = portal("awt", loader)

        manager.warmUpEngines()

        assertEquals(0, context.recordingScheduler.pendingAsync, "使用 AWT 的服主不应被拖上下载")
        assertEquals(0, loader.calls)
    }

    @Test
    fun `without a loader the gate still does not block the render path`() {
        val (manager, context) = portal("playwright", null)

        // 没有装载器时无法装载，但也不能在调用线程同步失败；配置了 playwright 就先放行到渲染路径，
        // 真实的 AWT 兜底与降级由渲染路径负责。
        assertTrue(manager.warmUpEngines(), "配置了 playwright 时不应把首帧直接判死")
        assertEquals(0, context.recordingScheduler.pendingAsync, "没有 loader 时不应调度装载任务")
        assertFalse(manager.isImageCapable && false, "此调用不得抛异常")
    }
}
