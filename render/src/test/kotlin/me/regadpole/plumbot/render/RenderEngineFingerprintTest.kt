/*
 *     PlumBot-V3
 *     Copyright (C) 2026  RegadPoleCN
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
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

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 引擎指纹**只能是纯读**：它每执行一次都会扫 34 个配置键，任何写回都会当场改坏运行中的配置。
 *
 * 回归背景（现场三端同时中招）：旧实现为了兼容 YAML 的四种标量类型，在 [RenderEngineFingerprint]
 * 里对每个键各读一次 `getBoolean`/`getInteger`/`getDouble`。configurate 的这三个属性是
 * **带默认值拷贝**的读法 —— 类型对不上就把默认值写回节点，于是 `render.engine: playwright`
 * 在首次 `configure()` 后变成 `Boolean false`：启动 3ms 后三端齐报「未知渲染引擎 'false'」、
 * 全部回落 AWT，doctor 读到 `browsers_path=…/false`、`custom_chrome_path=false` 而误判
 * `PW_CONFIG_INVALID`。
 *
 * 这里的 [NodeConfig] **故意保留** configurate 的默认（会写回的）读法：只要指纹再引入一次
 * 变异读取，下面第一条用例就会立刻变红。手写 FakeConfig 永远抓不到这个 bug。
 */
class RenderEngineFingerprintTest {

    /** 真实 YAML 节点支撑的 PlatformConfig；[hostileBooleanRead] = 用 configurate 默认的写回语义。 */
    private class NodeConfig(
        private val root: ConfigurationNode,
        private val hostileBooleanRead: Boolean
    ) : PlatformConfig {

        private fun node(path: Array<out String?>): ConfigurationNode = root.node(*path)

        override fun getString(vararg path: String?): String? = node(path).string
        override fun getLong(vararg path: String?): Long = node(path).long
        override fun getInteger(vararg path: String?): Int = node(path).int
        override fun getDouble(vararg path: String?): Double = node(path).double

        override fun getBoolean(vararg path: String?): Boolean {
            if (hostileBooleanRead) return node(path).boolean
            val target = node(path)
            if (target.virtual()) return false
            return when (val raw = target.raw()) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                is String -> raw.trim().equals("true", ignoreCase = true)
                else -> false
            }
        }

        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
        override fun contains(vararg path: String?): Boolean = !node(path).virtual()
        override fun rawTypeOf(vararg path: String?): String? {
            val target = node(path)
            if (target.virtual()) return "missing"
            return target.raw()?.javaClass?.simpleName ?: "null"
        }
    }

    private class TestContext(override val config: NodeConfig) : PlatformContext {
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = Files.createTempDirectory("plumbot-fingerprint-test")
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

        override val playerService: PlatformPlayerService get() = TODO("指纹测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) {
            logs += message
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false

        fun logged(fragment: String): Boolean = logs.any { it.contains(fragment) }
    }

    private fun yaml(text: String): ConfigurationNode {
        val file = Files.createTempFile("plumbot-fingerprint", ".yml")
        Files.writeString(file, text)
        return YamlConfigurationLoader.builder().path(file).build().load()
    }

    private val sampleYaml = """
        render:
          engine: playwright
          outputs:
            status: true
            list: true
          playwright:
            browsers_path: "%plugin_folder%/playwright-browsers"
            download_host: https://cdn.npmmirror.com/binaries/playwright
            theme: default
            install_shell: no-shell
            scale_factor: 1.5
            jpeg_quality: 85
            fallback_to_awt: true
            render_timeout_ms: 10000
    """.trimIndent()

    @Test
    fun `指纹连算两次不改变配置树且结果稳定`() {
        val root = yaml(sampleYaml)
        val config = NodeConfig(root, hostileBooleanRead = true)

        val first = RenderEngineFingerprint.of(config, null)
        val second = RenderEngineFingerprint.of(config, null)

        assertEquals(first, second, "同一份配置必须得到同一个指纹")
        assertTrue(first.contains("engine=playwright"), "引擎取值被改写成了别的类型：$first")
        assertTrue(first.contains("browsers_path=%plugin_folder%/playwright-browsers"), first)
        assertTrue(first.contains("download_host=https://cdn.npmmirror.com/binaries/playwright"), first)
        // 关键断言：指纹不得写回。旧实现跑完这两行后，下面每一行都会读到 false。
        assertEquals("String", config.rawTypeOf("render", "engine"), "指纹把 render.engine 写回了")
        assertEquals("playwright", config.getString("render", "engine"))
        assertEquals("String", config.rawTypeOf("render", "playwright", "browsers_path"))
        assertEquals("String", config.rawTypeOf("render", "playwright", "download_host"))
        assertEquals("String", config.rawTypeOf("render", "playwright", "theme"))
        assertEquals("String", config.rawTypeOf("render", "playwright", "install_shell"))
    }

    @Test
    fun `缺失的视图引擎键保持缺失且写成占位`() {
        val root = yaml(sampleYaml)
        val config = NodeConfig(root, hostileBooleanRead = true)

        val fingerprint = RenderEngineFingerprint.of(config, null)

        assertTrue(fingerprint.contains("view.status=-"), fingerprint)
        assertTrue(
            root.node("render", "views", "status", "engine").virtual(),
            "指纹不得把缺失键实体化成 Boolean false（那会让后续 getString 返回 \"false\"）"
        )
        assertEquals("missing", config.rawTypeOf("render", "views", "status", "engine"))
    }

    @Test
    fun `真正改变引擎配置必须改变指纹`() {
        val root = yaml(sampleYaml)
        val config = NodeConfig(root, hostileBooleanRead = true)
        val before = RenderEngineFingerprint.of(config, null)

        root.node("render", "playwright", "scale_factor").set(2.0)
        val afterScale = RenderEngineFingerprint.of(config, null)
        assertNotEquals(before, afterScale, "缩放因子变化必须触发引擎重建")

        root.node("render", "playwright", "fallback_to_awt").set(false)
        val afterFlag = RenderEngineFingerprint.of(config, null)
        assertNotEquals(afterScale, afterFlag, "布尔开关变化同样要进指纹")
    }

    @Test
    fun `configure 之后引擎仍解析为 playwright 而不是 false`() {
        val root = yaml(sampleYaml)
        val config = NodeConfig(root, hostileBooleanRead = false)
        val context = TestContext(config)
        val manager = ImageRenderManager(context)

        manager.configure(RenderConfig())

        assertFalse(
            context.logged("未知渲染引擎"),
            "configure 期间引擎取值被写坏了，实际日志：" + context.logs
        )
        assertEquals("playwright", config.getString("render", "engine"))
        assertEquals("String", config.rawTypeOf("render", "engine"))
        manager.shutdown()
    }
}
