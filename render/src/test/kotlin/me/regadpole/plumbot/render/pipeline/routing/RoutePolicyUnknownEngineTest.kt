// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.pipeline.engines.EngineRegistry
import me.regadpole.plumbot.render.pipeline.engines.PlaywrightLifecycle
import net.kyori.adventure.text.Component

/**
 * 未知引擎取值的解析契约：原样返回并一次性 WARN；实际的 AWT 回落由引擎表装配完成
 * （EngineRegistry.rendererFor），使"已回落 awt"的提示与出图行为一致。
 */
class RoutePolicyUnknownEngineTest {

    private val logs = mutableListOf<String>()

    private fun policy(values: Map<String, Any?>): RoutePolicy {
        val context = TestContext(values, logs)
        return RoutePolicy(context, NoticeSink(context))
    }

    @Test
    fun `未知引擎名原样返回并只告警一次`() {
        val policy = policy(mapOf("render.views.text.engine" to "playwrite"))

        assertEquals("playwrite", policy.engineFor(RenderRoute.TEXT))
        assertEquals(1, logs.size, "首次出现应告警一次：$logs")
        assertTrue(logs[0].contains("未知渲染引擎"), logs[0])

        assertEquals("playwrite", policy.engineFor(RenderRoute.TEXT))
        assertEquals(1, logs.size, "同一错拼去重后不再刷屏")
    }

    @Test
    fun `已知引擎取值不受影响`() {
        val policy = policy(
            mapOf(
                "render.views.status.engine" to "playwright",
                "render.views.text.engine" to "auto",
            )
        )
        assertEquals("playwright", policy.engineFor(RenderRoute.STATUS))
        // auto 视为未显式配置，TEXT 无路由首选 → 回落 awt。
        assertEquals("awt", policy.engineFor(RenderRoute.TEXT))
        assertEquals(0, logs.size, "已知取值不应产生未知引擎告警")
    }

    @Test
    fun `未知引擎名在引擎表回落到 awt 装配`() {
        val context = TestContext(mapOf("render.engine" to "playwrite"), logs)
        val policy = RoutePolicy(context, NoticeSink(context))
        val lifecycle = PlaywrightLifecycle(context, null, policy)
        val registry = EngineRegistry(
            context,
            lock = Any(),
            lifecycle = lifecycle,
            policy = policy,
            themeOf = { TODO("AWT 装配不触碰主题") },
            fontCssOf = { null },
            applyFont = {},
            refreshTheme = {},
            invalidateAvatars = {},
        )

        val renderer = assertNotNull(
            registry.rendererFor("playwrite"),
            "未知名不应返回 null，否则与 WARN 的回落承诺不符"
        )
        assertEquals("awt", renderer.type, "未知名应装配成 AWT 引擎")
        assertTrue(renderer === registry.rendererFor("playwrite"), "重复查找应复用同一实例，而不是反复构造")
    }

    private class TestContext(
        private val values: Map<String, Any?>,
        private val sink: MutableList<String>,
    ) : PlatformContext {
        override val config = TestConfig(values)
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: java.nio.file.Path =
            java.nio.file.Files.createTempDirectory("plumbot-route-policy-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("路由策略测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) { sink.add(message) }
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class TestConfig(private val values: Map<String, Any?>) : PlatformConfig {
        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        @Suppress("UNCHECKED_CAST")
        override fun getStringList(vararg path: String?): List<String?> =
            (values[key(path)] as? List<Any?>)?.map { it as? String } ?: emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }
}