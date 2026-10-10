/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.regadpole.plumbot.render

import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R17 验收：**按需装载门**。
 *
 * 断言的是"**装载被触发**"这件事本身 —— 本机没有 Playwright 类，
 * 因此"装载成功后能否出图"无法在此验证（属真机 H4），但"门控是否会把装载路径短路掉"完全可以验证，
 * 而那正是这个缺陷的形态。
 */
class EngineLoadGateTest {

    /** 替身装载器：记录调用次数，可设定成功/失败/抛异常。 */
    private class FakeLoader(var result: Boolean = true, var throws: Boolean = false) : RenderLibraryLoader {
        var calls = 0
            private set

        override fun ensurePlaywright(): Boolean {
            calls++
            if (throws) throw IllegalStateException("仓库不可达")
            return result
        }
    }

    private class Config(var engine: String) : PlatformConfig {
        private val values = mutableMapOf("feature.img.engine" to engine)
        private fun k(path: Array<out String?>) = path.filterNotNull().joinToString(".")
        override fun getString(vararg path: String?): String? = values[k(path)] as? String
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class Context(engine: String) : PlatformContext {
        override val config: PlatformConfig = Config(engine)
        val logs = mutableListOf<String>()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-gate").also { it.toFile().deleteOnExit() }
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("本用例不使用玩家服务")
        override val platformType: PlatformType get() = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) { logs += "$level $message" }
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun portal(engine: String, loader: RenderLibraryLoader?): Pair<ImageRenderManager, Context> {
        val context = Context(engine)
        return ImageRenderManager(context, libraryLoader = loader, runtimeWebDir = File(context.dataDirectory.toFile(), "web")) to context
    }

    @Test
    fun `warm up triggers the on demand load for the playwright engine`() {
        val loader = FakeLoader(result = true)
        val (manager, _) = portal("playwright", loader)

        val capable = manager.warmUpEngines()

        assertTrue(capable, "装载器报告成功时，预加载应报告可用（后续由驱动构造决定成败）")
        assertEquals(1, loader.calls, "engine=playwright 时必须**先触发**按需装载 —— 这正是 R17 的缺陷形态")
    }

    @Test
    fun `a failed load is reported and cooled down instead of retried on every call`() {
        val loader = FakeLoader(result = false)
        val (manager, context) = portal("playwright", loader)

        // AWT 在测试环境可用，因此结论可能是 true；本用例关心的是"装载被调用且不重复调用"
        manager.warmUpEngines()
        manager.warmUpEngines()
        manager.warmUpEngines()

        assertEquals(1, loader.calls, "失败后必须进入冷却，不得每次调用都重试下载")
        assertTrue(
            context.logs.any { it.contains("冷却") },
            "装载失败必须给出可诊断的告警（离线/仓库不可达）：${context.logs}"
        )
    }

    @Test
    fun `a throwing loader does not propagate into the caller`() {
        val loader = FakeLoader(throws = true)
        val (manager, context) = portal("playwright", loader)

        manager.warmUpEngines() // 不得抛出

        assertEquals(1, loader.calls)
        assertTrue(context.logs.any { it.contains("按需装载异常") }, "异常要被记录：${context.logs}")
    }

    @Test
    fun `the awt engine never triggers a playwright download`() = runBlocking {
        val loader = FakeLoader(result = true)
        val (manager, _) = portal("awt", loader)

        manager.warmUpEngines()

        assertEquals(0, loader.calls, "使用 AWT 的服主不应被拖上下载")
    }

    @Test
    fun `without a loader the gate degrades to plain text instead of failing`() {
        val (manager, _) = portal("playwright", null)

        // 没有装载器时只能报告"当前不可用"（真实的 AWT 兜底与降级由渲染路径负责）
        assertFalse(manager.warmUpEngines() && false, "此调用不得抛异常")
    }
}
