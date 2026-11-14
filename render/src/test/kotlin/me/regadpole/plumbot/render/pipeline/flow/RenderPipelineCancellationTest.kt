// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.flow

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.TextPayload
import me.regadpole.plumbot.render.engine.ImageRenderer
import me.regadpole.plumbot.render.engine.RenderOptions
import me.regadpole.plumbot.render.pipeline.engines.EngineRegistry
import me.regadpole.plumbot.render.pipeline.engines.PlaywrightLifecycle
import me.regadpole.plumbot.render.pipeline.routing.NoticeSink
import me.regadpole.plumbot.render.pipeline.routing.RoutePolicy
import me.regadpole.plumbot.render.theme.ThemeManager
import net.kyori.adventure.text.Component

/**
 * 取消传播契约：协程被取消（withTimeout / 结构化取消）时渲染必须原样上抛
 * CancellationException，主引擎与 AWT 兜底两条路径都不允许把"正在被取消"
 * 当成"引擎失败"吞掉再兜底一次。
 */
class RenderPipelineCancellationTest {

    private class CancellingRenderer : ImageRenderer {
        val attempts = AtomicInteger()
        override val type = "awt"
        override val isSupported = true
        override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? {
            attempts.incrementAndGet()
            throw CancellationException("job cancelled")
        }
    }

    private fun pipeline(values: Map<String, Any?>, renderer: CancellingRenderer): RenderPipeline {
        val ctx = TestContext(values)
        val notices = NoticeSink(ctx)
        val policy = RoutePolicy(ctx, notices)
        val lifecycle = PlaywrightLifecycle(ctx, null, policy)
        val registry = EngineRegistry(
            ctx, Any(), lifecycle, policy,
            themeOf = { ThemeManager(ctx, Files.createTempDirectory("plumbot-cancel-test").toFile()) },
            fontCssOf = { null },
            applyFont = {},
            refreshTheme = {},
            invalidateAvatars = {}
        )
        registry.register(renderer)
        return RenderPipeline(ctx, policy, registry, lifecycle, notices, CapacityGate(ctx))
    }

    @Test
    fun `主引擎抛出取消时原样上抛而不降级`() {
        val renderer = CancellingRenderer()
        val pipeline = pipeline(mapOf("render.outputs.text" to true), renderer)

        val cause = runBlocking {
            runCatching {
                pipeline.render(RenderRequest(RenderRoute.TEXT.route, TextPayload("hello")))
            }.exceptionOrNull()
        }

        assertTrue(cause is CancellationException, "取消必须原样上抛，实际得到：$cause")
        assertEquals("job cancelled", cause!!.message, "上抛的应是渲染体抛出的那个取消")
        assertEquals(1, renderer.attempts.get(), "取消不得触发 AWT 兜底重渲染")
    }

    @Test
    fun `兜底引擎抛出取消时同样原样上抛`() {
        val renderer = CancellingRenderer()
        val pipeline = pipeline(
            mapOf("render.outputs.text" to true, "render.views.text.engine" to "broken"),
            renderer
        )

        // "broken" 引擎不可用 → primary 为 null → 走 AWT 兜底；兜底抛出的取消也不能被吞成 Failed
        val cause = runBlocking {
            runCatching {
                pipeline.render(RenderRequest(RenderRoute.TEXT.route, TextPayload("hello")))
            }.exceptionOrNull()
        }

        assertTrue(cause is CancellationException, "兜底路径的取消必须原样上抛，实际得到：$cause")
        assertEquals(1, renderer.attempts.get(), "兜底引擎应被调用恰好一次")
    }

    private class TestConfig(private val values: Map<String, Any?>) : PlatformConfig {
        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext(values: Map<String, Any?>) : PlatformContext {
        override val config = TestConfig(values)
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: java.nio.file.Path = Files.createTempDirectory("plumbot-cancel-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("取消测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }
}