// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.render.install.PlaywrightInstallGate

/**
 * **装载门**（[PlaywrightInstallGate]）。
 *
 * 断言的是门本身的三条不变式：非阻塞调度、单飞、成功后不再触发 / 失败后冷却。
 * "真机下载 190 MB 之后能否出图"不在这里验证，属真机验证项。
 */
class PlaywrightInstallGateTest {

    /** 替身装载器：记录调用次数与执行线程，可设定成功、失败原因、抛异常。 */
    private class FakeLoader(
        var result: Boolean = true,
        var failure: String? = null,
        var throws: Boolean = false
    ) : RenderLibraryLoader {
        var calls = 0
            private set
        val threads = mutableListOf<Thread>()

        override fun ensurePlaywright(): Boolean {
            calls++
            threads += Thread.currentThread()
            if (throws) throw IllegalStateException("仓库不可达")
            return result
        }

        override fun lastFailure(): String? = failure
    }

    /** 可控调度器：只入队，测试手动 drain（用独立 worker 线程，证明装载不在调用线程）。 */
    private class RecordingLane {
        private val tasks = ArrayDeque<() -> Unit>()
        val pending: Int get() = tasks.size
        val threads = mutableListOf<Thread>()

        val schedule: (() -> Unit) -> Unit = { task -> tasks.addLast(task) }

        fun drainOnWorker() {
            while (tasks.isNotEmpty()) {
                val task = tasks.removeFirst()
                val worker = Thread({
                    threads += Thread.currentThread()
                    task()
                }, "plumbot-test-install-gate").also { it.isDaemon = true }
                worker.start()
                worker.join()
            }
        }
    }

    private class Logs {
        val lines = mutableListOf<String>()
        val sink: (LogLevel, String) -> Unit = { level, message -> lines += "$level $message" }
    }

    private fun gate(
        loader: RenderLibraryLoader?,
        lane: RecordingLane,
        logs: Logs = Logs(),
        clock: () -> Long = { 0L },
        cooldownMillis: Long = 60_000L
    ) = PlaywrightInstallGate(
        loader = loader,
        scheduleAsync = lane.schedule,
        log = logs.sink,
        clock = clock,
        cooldownMillis = cooldownMillis
    )

    @Test
    fun `startOnce returns immediately and runs the loader on the async lane`() {
        val loader = FakeLoader()
        val lane = RecordingLane()
        val gate = gate(loader, lane)

        assertTrue(gate.startOnce("test"), "首次请求应当被接受")

        assertEquals(0, loader.calls, "startOnce 绝不能在调用线程同步装载")
        assertEquals(1, lane.pending)
        assertTrue(gate.isLoading(), "调度之后应处于 RUNNING")
        assertFalse(gate.isReady())

        lane.drainOnWorker()

        assertEquals(1, loader.calls)
        assertEquals(1, loader.threads.size)
        assertFalse(
            loader.threads.single() === Thread.currentThread(),
            "装载必须发生在平台异步泳道的线程上，而不是调用线程"
        )
        assertTrue(gate.isReady())
    }

    @Test
    fun `a second startOnce while loading is refused`() {
        val loader = FakeLoader()
        val lane = RecordingLane()
        val gate = gate(loader, lane)

        assertTrue(gate.startOnce("warm-up"))
        assertFalse(gate.startOnce("warm-up"), "装载中不得重复入队")
        assertEquals(1, lane.pending)

        lane.drainOnWorker()
        assertEquals(1, loader.calls)
    }

    @Test
    fun `a ready gate refuses further requests`() {
        val loader = FakeLoader()
        val lane = RecordingLane()
        val gate = gate(loader, lane)

        assertTrue(gate.startOnce("warm-up"))
        lane.drainOnWorker()
        assertTrue(gate.isReady())

        assertFalse(gate.startOnce("render"), "已就绪后不应再触发装载")
        assertEquals(0, lane.pending)
        assertEquals(1, loader.calls)
        assertNull(gate.awaitLoad(), "已就绪时阻塞入口也应直接返回 null")
    }

    @Test
    fun `a failure enters cooling and blocks retries until the cooldown expires`() {
        val loader = FakeLoader(result = false, failure = "driver-bundle: 连接超时")
        val lane = RecordingLane()
        val logs = Logs()
        var now = 1_000L
        val gate = gate(loader, lane, logs, clock = { now }, cooldownMillis = 60_000L)

        assertTrue(gate.startOnce("warm-up"))
        lane.drainOnWorker()

        assertFalse(gate.isReady())
        assertTrue(gate.isCooling(), "失败后必须进入冷却")
        assertEquals(60_000L, gate.coolingRemainingMillis())
        assertEquals("driver-bundle: 连接超时", gate.failureReason())
        assertTrue(logs.lines.any { "冷却" in it }, "冷却必须留一条 WARN 证据：${logs.lines}")

        assertFalse(gate.startOnce("render"), "冷却期内不得重试下载")
        assertEquals(0, lane.pending)

        now += 60_000L
        assertFalse(gate.isCooling(), "冷却到期后不再是冷却态")
        assertTrue(gate.startOnce("render"), "冷却到期后允许重试")
        lane.drainOnWorker()
        assertEquals(2, loader.calls)
    }

    @Test
    fun `awaitLoad loads synchronously and reports success`() {
        val loader = FakeLoader()
        val lane = RecordingLane()
        val gate = gate(loader, lane)

        val result = gate.awaitLoad()

        assertNull(result)
        assertEquals(1, loader.calls, "阻塞入口自己完成装载，不走异步泳道")
        assertEquals(0, lane.pending)
        assertTrue(gate.isReady())
    }

    @Test
    fun `awaitLoad reports the loader failure and the cooling gate repeats it`() {
        val loader = FakeLoader(result = false, failure = "playwright: 404")
        val gate = gate(loader, RecordingLane(), cooldownMillis = 60_000L)

        assertEquals("playwright: 404", gate.awaitLoad())
        assertTrue(gate.isCooling())

        // 冷却期内再次调用直接返回上次原因，不再摸 loader
        assertEquals("playwright: 404", gate.awaitLoad())
        assertEquals(1, loader.calls)
    }

    @Test
    fun `an exception from the loader is folded into a reason`() {
        val loader = FakeLoader(throws = true)
        val logs = Logs()
        val gate = gate(loader, RecordingLane(), logs)

        val reason = gate.awaitLoad()

        assertTrue(reason != null && "按需装载异常" in reason, "异常必须折成可展示原因：$reason")
        assertTrue(logs.lines.any { "按需装载异常" in it }, "异常路径要留 WARN：${logs.lines}")
        assertTrue(gate.isCooling())
    }

    @Test
    fun `markReady bypasses loading`() {
        val loader = FakeLoader()
        val lane = RecordingLane()
        val gate = gate(loader, lane)

        gate.markReady()

        assertTrue(gate.isReady())
        assertFalse(gate.startOnce("warm-up"))
        assertNull(gate.awaitLoad())
        assertEquals(0, loader.calls)
    }

    @Test
    fun `a scheduling failure is logged and the gate returns to idle`() {
        val loader = FakeLoader()
        val logs = Logs()
        val gate = PlaywrightInstallGate(
            loader = loader,
            scheduleAsync = { throw IllegalStateException("调度器已关闭") },
            log = logs.sink
        )

        assertFalse(gate.startOnce("warm-up"))
        assertFalse(gate.isLoading(), "调度失败必须回到 IDLE，否则门会永久卡住")
        assertTrue(logs.lines.any { "无法调度" in it }, "调度失败要留 WARN：${logs.lines}")
        assertEquals(0, loader.calls)
    }

    @Test
    fun `a missing loader is refused instead of pretending to succeed`() {
        val lane = RecordingLane()
        val gate = gate(loader = null, lane = lane)

        assertFalse(gate.startOnce("warm-up"))
        assertEquals(0, lane.pending, "没有装载器就什么都不该调度")
        val reason = gate.awaitLoad()
        assertTrue(reason != null && "未注入 RenderLibraryLoader" in reason, "原因需可直接展示：$reason")
    }
}
