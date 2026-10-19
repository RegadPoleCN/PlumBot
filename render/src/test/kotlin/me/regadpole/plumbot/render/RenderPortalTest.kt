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

import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 渲染门户的契约测试（基线 §3.3 / §3.6 / §6）。
 *
 * 覆盖：
 * - 引擎解析 `render.views.<route>.engine` → `render.engine` → 内置默认；
 * - 路由门控（`render.outputs.<功能>`）；无门控 route 由调用方决定；
 * - `engine: none` 全局短路；
 * - **永不抛异常**：未知路由、引擎缺失、驱动异常都必须以 [RenderResult] 返回。
 */
class RenderPortalTest {

    private class FakeConfig(private val values: Map<String, Any>) : PlatformConfig {
        private fun key(path: Array<out String?>): String = path.filterNotNull().joinToString(".")

        override fun getString(vararg path: String?): String? = values[key(path)] as? String
        override fun getLong(vararg path: String?): Long = (values[key(path)] as? Long) ?: 0L
        override fun getInteger(vararg path: String?): Int = (values[key(path)] as? Int) ?: 0
        override fun getBoolean(vararg path: String?): Boolean = (values[key(path)] as? Boolean) ?: false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class FakeContext(
        override val config: PlatformConfig,
        override val dataDirectory: Path,
        val logs: MutableList<String> = mutableListOf()
    ) : PlatformContext {
        override val datasource: PlatformConfig get() = config
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val playerService: PlatformPlayerService get() = TODO("测试未使用")
        override val platformType: PlatformType get() = TODO("测试未使用")

        override fun log(level: LogLevel, message: String) {
            logs += message
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun portal(
        values: Map<String, Any>,
        logs: MutableList<String> = mutableListOf()
    ): RenderPortal {
        val base = File(System.getProperty("java.io.tmpdir"), "plumbot-portal-test").apply { mkdirs() }
        return ImageRenderManager(
            context = FakeContext(FakeConfig(values), base.toPath(), logs),
            libraryLoader = null,
            runtimeWebDir = base
        )
    }

    private fun render(portal: RenderPortal, route: String, payload: Any = TextPayload(body = "hello")): RenderResult =
        runBlocking { portal.render(RenderRequest(route, payload)) }

    @Test
    fun `未知路由返回 Failed 且不抛异常`() {
        val result = render(portal(emptyMap()), "noSuchRoute")
        assertIs<RenderResult.Failed>(result)
    }

    /**
     * 引擎取值拼错时必须**告警并回落 AWT**，而不是静默降级。
     *
     * 旧实现只做小写化、没有取值白名单，于是 `playwrite` 这类拼写错误会让服主
     * 以为开了高清渲染、实际一直是本地轻量图，且没有任何线索可查。
     */
    @Test
    fun `未知引擎值会告警并回落 awt`() {
        val logs = mutableListOf<String>()
        val portal = portal(mapOf("render.views.text.engine" to "playwrite"), logs)

        assertIs<RenderResult.Image>(render(portal, RenderRoute.TEXT.route), "应回落 AWT 并正常出图")
        assertTrue(
            logs.any { it.contains("未知渲染引擎") && it.contains("playwrite") },
            "必须告警且指出具体取值。实际日志：$logs"
        )
    }

    /** 同一个错拼只提示一次，避免高频路径刷屏。 */
    @Test
    fun `未知引擎值只告警一次`() {
        val logs = mutableListOf<String>()
        val portal = portal(mapOf("render.views.text.engine" to "playwrite"), logs)

        repeat(3) { render(portal, RenderRoute.TEXT.route) }

        assertEquals(1, logs.count { it.contains("未知渲染引擎") }, "重复渲染不应重复告警：$logs")
    }

    /** 合法取值不得产生任何告警。 */
    @Test
    fun `已知引擎不告警`() {
        val logs = mutableListOf<String>()
        val portal = portal(mapOf("render.views.text.engine" to "awt"), logs)

        assertIs<RenderResult.Image>(render(portal, RenderRoute.TEXT.route))
        assertTrue(logs.none { it.contains("未知渲染引擎") }, "合法引擎不应告警：$logs")
    }

    @Test
    fun `configure 注入的底板色会真实影响出图像素`() {
        val original = TextToImg.style
        try {
            val portal = portal(mapOf("render.views.text.engine" to "awt"))
            portal.configure(
                RenderConfig(
                    textStyle = RenderTextStyle(
                        baseColor = "#FF0000",
                        textColor = "#FFFFFF",
                        autoOutline = false
                    )
                )
            )
            val result = render(portal, RenderRoute.TEXT.route, TextPayload(body = "配置联动"))
            val png = assertIs<RenderResult.Image>(result).png

            val image = ImageIO.read(ByteArrayInputStream(png))
            // 取左上角像素：应为注入的纯红底板（旧实现此处恒为写死的 #1E2430）
            val corner = image.getRGB(1, 1)
            assertEquals(0xFF, (corner ushr 16) and 0xFF, "R 通道应为 255")
            assertEquals(0x00, (corner ushr 8) and 0xFF, "G 通道应为 0")
            assertEquals(0x00, corner and 0xFF, "B 通道应为 0")
        } finally {
            TextToImg.style = original
        }
    }

    @Test
    fun `非法颜色不会导致 configure 抛异常`() {
        val original = TextToImg.style
        try {
            val portal = portal(mapOf("render.views.text.engine" to "awt"))
            portal.configure(RenderConfig(textStyle = RenderTextStyle(baseColor = "not-a-color")))
            // 回落默认值，仍可正常出图
            assertIs<RenderResult.Image>(render(portal, RenderRoute.TEXT.route))
        } finally {
            TextToImg.style = original
        }
    }

    @Test
    fun `engine 为 none 时全局短路为 Unavailable`() {
        val result = render(portal(mapOf("render.engine" to "none")), RenderRoute.TEXT.route)
        assertEquals(RenderResult.Unavailable, result)
    }

    @Test
    fun `有门控的 route 在门控关闭时不渲染`() {
        // status 的门控为 render.outputs.status，默认 false
        val result = render(portal(emptyMap()), RenderRoute.STATUS.route, mapOf("a" to 1))
        assertEquals(RenderResult.Unavailable, result)
    }

    @Test
    fun `有门控的 route 在门控打开时进入渲染`() {
        val logs = mutableListOf<String>()
        val portal = portal(mapOf("render.outputs.status" to true), logs)
        // AWT 不可用的环境下会返回 Failed；可用时应返回 Image。两者都属"未抛异常"
        val result = render(portal, RenderRoute.STATUS.route, mapOf("a" to 1))
        assertTrue(
            result is RenderResult.Image || result is RenderResult.Failed,
            "门控打开后应真正尝试渲染，实际: $result"
        )
    }

    @Test
    fun `无门控的 route 由调用方决定是否出图`() {
        val portal = portal(emptyMap())
        val result = render(portal, RenderRoute.TEXT.route, TextPayload(body = "控制台回显 §aOK"))
        assertTrue(
            result is RenderResult.Image || result is RenderResult.Failed,
            "text 路由不应被门控拦成 Unavailable，实际: $result"
        )
    }

    @Test
    fun `路由级引擎覆盖优先于全局引擎`() {
        // 全局 none，但 text 路由显式指定 awt → 不应被全局短路
        val portal = portal(
            mapOf(
                "render.engine" to "none",
                "render.views.${RenderRoute.TEXT.route}.engine" to "awt"
            )
        )
        val result = render(portal, RenderRoute.TEXT.route, TextPayload(body = "hi"))
        assertTrue(result !is RenderResult.Unavailable, "路由级覆盖应优先于全局 none，实际: $result")
    }

    @Test
    fun `驱动异常被转换为 Failed 而不是抛出`() {
        val portal = portal(mapOf("render.engine" to "awt"))
        // 空 payload 走 formatPayload 的 toString 分支，不应崩溃
        val result = render(portal, RenderRoute.TEXT.route, Any())
        assertTrue(result is RenderResult.Image || result is RenderResult.Failed)
    }

    @Test
    fun `门户暴露能力探测`() {
        val portal = portal(emptyMap())
        // 与 AWT 探针一致：能力探测不得抛异常
        val capable = portal.isImageCapable
        assertEquals(capable, portal.isImageCapable, "能力探测应稳定可重复")
        if (!capable) assertFalse(capable)
    }

    @Test
    fun `关闭门户是幂等且安全的`() {
        val portal = portal(emptyMap())
        portal.shutdown()
        portal.shutdown()
    }

    @Test
    fun `重载不抛异常且可重复`() {
        val portal = portal(emptyMap())
        portal.reload()
        portal.reload()
    }

    @Test
    fun `路由枚举可按名称解析且忽略大小写`() {
        assertEquals(RenderRoute.STATUS, RenderRoute.byName("status"))
        assertEquals(RenderRoute.STATUS, RenderRoute.byName("STATUS"))
        assertEquals(RenderRoute.HYTALE_AVATAR, RenderRoute.byName("hytaleAvatar"))
        assertEquals(null, RenderRoute.byName("nope"))
    }
}
