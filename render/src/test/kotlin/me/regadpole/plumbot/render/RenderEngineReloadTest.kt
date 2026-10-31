// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
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
import me.regadpole.plumbot.render.pipeline.RenderTeardown
import net.kyori.adventure.text.Component

/**
 * 引擎重建只看配置指纹，且推迟到下一次真正需要引擎时（懒重建）。
 *
 * 背景：旧实现在每次 /plumbot reload 都无条件清空引擎表，于是连「只改了一行无关配置」
 * 都会关闭并重启 Chromium（同步等待 node 驱动退出，进行中的出图降级为文本），
 * 还留下一行「底层 Chromium 意外退出」的假故障。这里用可计数的假引擎把两条语义钉死：
 * 配置没变 → 一个引擎都不许关；配置变了 → reload 当时也不许关，等下次取引擎再回收。
 */
class RenderEngineReloadTest {

    private class FakeEngine : ImageRenderer {
        override val type: String = "fake-reload-engine"
        override val isSupported: Boolean = true
        val shutdownCount = AtomicInteger()

        override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? = null

        override fun shutdown() {
            shutdownCount.incrementAndGet()
        }
    }

    /** 可写的点分配置：用于模拟 reload 前后同一份配置有没有变化。 */
    private class MapConfig : PlatformConfig {
        val values = mutableMapOf<String, Any>()

        private fun key(path: Array<out String?>): String = path.joinToString(".")

        // 与真实 YamlConfigurator 一致：非字符串标量也要能转成文本（引擎指纹只读文本）。
        override fun getString(vararg path: String?): String? = values[key(path)]?.toString()
        override fun contains(vararg path: String?): Boolean = key(path) in values
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Number)?.toLong() ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Number)?.toInt() ?: 0
        override fun getBoolean(vararg path: String?): Boolean = values[key(path)] as? Boolean ?: false
        override fun getDouble(vararg path: String?): Double = (values[key(path)] as? Number)?.toDouble() ?: Double.NaN
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class RecordingContext : PlatformContext {
        override val config = MapConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = Files.createTempDirectory("plumbot-reload-test")
        val logs = Collections.synchronizedList(mutableListOf<String>())

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

        override val playerService: PlatformPlayerService get() = TODO("重载指纹测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) {
            logs += message
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false

        fun logged(fragment: String): Boolean = logs.any { it.contains(fragment) }
    }

    @Test
    fun `配置未变化时 reload 保留现有引擎且不关闭 Chromium`() {
        val context = RecordingContext()
        val fake = FakeEngine()
        val portal = ImageRenderManager(context).also { it.registerEngineForTest(fake) }

        portal.configure(RenderConfig())
        portal.reload()

        assertEquals(0, fake.shutdownCount.get(), "配置未变化时不得关闭任何引擎")
        assertTrue(
            context.logged("渲染配置未变化"),
            "应记录一条保留现有引擎的 INFO，实际日志：" + context.logs
        )
        portal.shutdown()
    }

    @Test
    fun `配置变化时 reload 只标记懒重建并在下次取引擎时回收旧引擎`() {
        val context = RecordingContext()
        val fake = FakeEngine()
        val portal = ImageRenderManager(context).also { it.registerEngineForTest(fake) }
        portal.configure(RenderConfig())

        // 改一项构造期一次性读取的引擎配置（缩放因子）。
        context.config.values["render.playwright.scale_factor"] = 2
        portal.configure(RenderConfig())
        portal.reload()

        assertEquals(0, fake.shutdownCount.get(), "reload 本身不得关闭引擎（懒重建）")
        assertTrue(
            context.logged("引擎将在下一次出图前按新配置重建"),
            "应记录一条懒重建的 INFO，实际日志：" + context.logs
        )

        // 下一次真正需要引擎时才摘掉并回收旧实例。
        assertTrue(portal.isImageCapable, "AWT 引擎必须可用")
        assertTrue(RenderTeardown.awaitIdle(5_000), "回收队列未在超时前排空")
        assertEquals(1, fake.shutdownCount.get(), "旧引擎应恰好被回收一次")
        assertTrue(
            context.logged("引擎已按新配置重建"),
            "应记录一条重建完成的 INFO，实际日志：" + context.logs
        )
        portal.shutdown()
    }
}
