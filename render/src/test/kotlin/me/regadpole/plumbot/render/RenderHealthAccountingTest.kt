// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.RenderOptions
import me.regadpole.plumbot.render.pipeline.ImageRenderManager
import me.regadpole.plumbot.render.pipeline.RenderMetrics
import net.kyori.adventure.text.Component

/**
 * 渲染健康计数的**分类口径**（门户是唯一计数者）。
 *
 * 背景：旧实现里门户只加 `render.fallback`（AWT 兜底），而群卡片读的是业务层的
 * `HealthCounters.RENDER_FALLBACK`（只在 `warmUpEngines()` 失败时加），于是"真降级了却
 * 显示降级 0"。现在改为门户统一给出 出图/降级/失败/队列拒绝/引擎未就绪，本用例把
 * 每类结果的落点钉死。
 */
class RenderHealthAccountingTest {

    private class FakeRenderer(
        override val type: String,
        private val outcome: () -> ByteArray?
    ) : ImageRenderer {
        override val isSupported: Boolean = true

        override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? = outcome()
    }

    private class TestConfig(private val values: Map<String, Any>) : PlatformConfig {
        private fun k(path: Array<out String?>) = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[k(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[k(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[k(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[k(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext(values: Map<String, Any>) : PlatformContext {
        override val config: PlatformConfig = TestConfig(values)
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-health-test")
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
        override val playerService: PlatformPlayerService get() = TODO("计数测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun tinyPng(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB), "png", out)
        return out.toByteArray()
    }

    private fun request() = RenderRequest(
        route = RenderRoute.STATUS.route,
        payload = mapOf("serverName" to "test"),
        targetKey = "group:10001"
    )

    @BeforeTest
    fun resetMetrics() = RenderMetrics.reset()

    @AfterTest
    fun cleanup() = RenderMetrics.reset()

    @Test
    fun `成功出图只计入 emitted`() = runBlocking {
        val context = TestContext(mapOf("render.engine" to "fake-ok", "render.outputs.status" to true))
        val portal = ImageRenderManager(context).also {
            it.registerEngineForTest(FakeRenderer("fake-ok") { tinyPng() })
        }

        assertTrue(portal.render(request()) is RenderResult.Image)

        val health = portal.renderHealth()
        assertEquals(1L, health.emitted)
        assertEquals(0L, health.degraded, "成功出图不算降级")
        assertEquals(0L, health.failed)
        assertEquals(null, health.lastFailure)
    }

    @Test
    fun `主引擎失败且禁用 AWT 兜底时计入 failed 与 degraded 并留下原因`() = runBlocking {
        val context = TestContext(
            mapOf(
                "render.engine" to "fake-broken",
                "render.outputs.status" to true,
                "render.playwright.fallback_to_awt" to "false"
            )
        )
        val portal = ImageRenderManager(context).also {
            it.registerEngineForTest(FakeRenderer("fake-broken") { throw IllegalStateException("boom") })
        }

        val result = portal.render(request())
        assertTrue(result is RenderResult.Failed, "禁兜底时必须是 Failed 而不是悄悄出 AWT 图: $result")

        val health = portal.renderHealth()
        assertEquals(1L, health.failed, "最终没有图就是 failed")
        assertEquals(1L, health.degraded, "主引擎确实失败过，属于降级")
        assertEquals(0L, health.fallbackAwt, "禁兜底时不该记 AWT 兜底")
        assertEquals(0L, health.emitted)
        val lastFailure = assertNotNull(health.lastFailure, "失败原因必须留档，否则线上无从查起")
        assertTrue(lastFailure.contains("fake-broken"), "原因里应带引擎名: $lastFailure")
        assertTrue(lastFailure.contains("boom"), "原因里应带原始异常信息: $lastFailure")
    }

    @Test
    fun `重置后计数归零`() {
        RenderMetrics.increment(RenderMetrics.EMITTED)
        RenderMetrics.noteFailure("status", "demo")

        RenderMetrics.reset()

        val health = RenderMetrics.health()
        assertEquals(0L, health.emitted)
        assertEquals(null, health.lastFailure)
    }
}
