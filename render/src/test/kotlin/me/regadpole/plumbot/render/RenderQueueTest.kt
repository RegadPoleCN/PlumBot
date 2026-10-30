// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **队列容量**与**同目标串行**。两件事必须成立：
 * 1. 容量 = 并发 2 + 排队 6（共 8）；
 *    超出立即降级为 `Unavailable`（而不是无限排队把内存吃光）；
 * 2. **同一目标**（群/用户）的出图必须串行 —— 修复前 `targetLocks.clear()` 会把别人正在持有的
 *    Mutex 一起丢掉，"串行"保证失效；而**不同目标**之间仍应能并发（否则全局退化为单线程）。
 *
 * 用可控的慢引擎来构造并发，因此结论与机器快慢无关。
 */
class RenderQueueTest {

    private class SlowRenderer(private val delayMs: Long) : ImageRenderer {
        override val type: String = "fake"
        override val isSupported: Boolean = true

        val started = AtomicInteger()
        val maxInFlight = AtomicInteger()
        private val inFlight = AtomicInteger()

        override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? {
            started.incrementAndGet()
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, now) }
            try {
                delay(delayMs)
                return tinyPng()
            } finally {
                inFlight.decrementAndGet()
            }
        }

        private fun tinyPng(): ByteArray {
            val out = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", out)
            return out.toByteArray()
        }
    }

    private class TestConfig : PlatformConfig {
        val values = mutableMapOf<String, Any>()
        private fun k(path: Array<out String?>) = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[k(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[k(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[k(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[k(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext : PlatformContext {
        override val config = TestConfig().apply {
            values["render.engine"] = "fake"
            values["render.outputs.status"] = true
        }
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-queue-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) =
                PlatformTaskHandle { true }

            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) =
                PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("队列测试未使用玩家服务")
        override val platformType: PlatformType get() = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun portalWith(renderer: SlowRenderer): RenderPortal =
        ImageRenderManager(TestContext()).also { it.registerEngineForTest(renderer) }

    @BeforeTest
    fun resetMetrics() {
        // RenderMetrics 是进程级单例：同一个 JVM 里其它用例的拒绝数会污染这里的等式断言。
        RenderMetrics.reset()
    }

    private fun request(targetKey: String?) = RenderRequest(
        route = RenderRoute.STATUS.route,
        payload = mapOf("serverName" to "test"),
        targetKey = targetKey
    )

    @Test
    fun `renders beyond capacity are rejected immediately instead of queuing forever`() {
        val renderer = SlowRenderer(delayMs = 200)
        val portal = portalWith(renderer)

        // 容量常量在 private companion 内，测试用字面量与注释保持同步（2 + 6 = 8）
        val attempts = 12
        val results = runBlocking {
            (1..attempts).map { index ->
                async(Dispatchers.IO) { portal.render(request("user:$index")) }
            }.awaitAll()
        }

        val rejected = results.count { it is RenderResult.Unavailable }
        assertTrue(rejected >= 1, "超出容量（$attempts > 8）必须有请求被拒绝，实际拒绝 $rejected 个")
        assertTrue(
            results.any { it is RenderResult.Image },
            "容量内的请求仍须正常出图（拒绝不能波及所有人）"
        )
        assertEquals(
            rejected.toLong(),
            portal.metrics()["render.queueRejected"],
            "拒绝次数必须被计数（否则线上看不出'一直在丢请求'）"
        )
        assertEquals(
            rejected.toLong(),
            portal.renderHealth().queueRejected,
            "队列拒绝必须同时进健康口径（/plumbot status 读的是 renderHealth）"
        )
    }

    @Test
    fun `the same target renders serially`() {
        val renderer = SlowRenderer(delayMs = 80)
        val portal = portalWith(renderer)

        runBlocking {
            (1..6).map { async(Dispatchers.IO) { portal.render(request("group:10001")) } }.awaitAll()
        }

        assertEquals(6, renderer.started.get(), "6 次请求都应被执行")
        assertEquals(1, renderer.maxInFlight.get(), "同一目标的出图必须串行（修复前 targetLocks.clear() 会让它并发）")
    }

    @Test
    fun `different targets can still render concurrently`() {
        val renderer = SlowRenderer(delayMs = 120)
        val portal = portalWith(renderer)

        runBlocking {
            (1..3).map { async(Dispatchers.IO) { portal.render(request("group:$it")) } }.awaitAll()
        }

        assertTrue(
            renderer.maxInFlight.get() >= 2,
            "不同目标之间必须仍能并发（实测最大并发 ${renderer.maxInFlight.get()}）；否则等于全局单线程"
        )
    }
}
