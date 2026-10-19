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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **端到端集成测试**（台账 U-13）。
 *
 * 此前的 100+ 项单测全部集中在纯逻辑（颜色、守卫、治理、JSON、裁头、缓存），
 * "载荷 → 引擎 → PNG 字节"这条主链路**零覆盖**。本测试补上这一段：
 * 用真实的 AWT 引擎跑完整渲染，断言产出是**可解码的有效 PNG**、尺寸合理、
 * 且超大载荷经过治理后仍能出图而不是崩溃。
 *
 * 未覆盖的部分（需真实 QQ / 浏览器 / Hytale 服务端）见台账 U-11 / U-12。
 */
class RenderPipelineIntegrationTest {

    // ---------------- 测试替身 ----------------

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
        override val playerService: PlatformPlayerService = EmptyPlayerService,
        val logs: MutableList<String> = mutableListOf()
    ) : PlatformContext {
        override val datasource: PlatformConfig get() = config
        override val scheduler: PlatformScheduler get() = TODO("测试未使用")
        override val platformType: PlatformType get() = TODO("测试未使用")

        override fun log(level: LogLevel, message: String) {
            logs += message
        }

        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    /** 三个皮肤来源全部返回 null 的默认实现（让漏斗在**不发起任何 HTTP** 的前提下走完三级）。 */
    private object EmptyPlayerService : PlatformPlayerService {
        override fun kickPlayer(name: String) = Unit
        override fun listPlayers(): List<String> = emptyList()
        override fun listPlayerString(): String = ""
    }

    /**
     * 计数用的平台来源替身。
     *
     * 三个来源都返回 `null` —— [AvatarResolver.level] 在 provider 返回 null 时**直接返回**，
     * 不会去抓取纹理，因此整条漏斗在测试中零网络访问。
     */
    private class CountingPlayerService : PlatformPlayerService {
        val pluginCalls = java.util.concurrent.atomic.AtomicInteger()
        val profileCalls = java.util.concurrent.atomic.AtomicInteger()
        val premiumCalls = java.util.concurrent.atomic.AtomicInteger()

        override fun kickPlayer(name: String) = Unit
        override fun listPlayers(): List<String> = emptyList()
        override fun listPlayerString(): String = ""

        override fun skinTextureFromPlugin(playerName: String): String? {
            pluginCalls.incrementAndGet()
            return null
        }

        override fun skinTextureFromProfile(playerName: String): String? {
            profileCalls.incrementAndGet()
            return null
        }

        override fun premiumUuid(playerName: String): String? {
            premiumCalls.incrementAndGet()
            return null
        }
    }

    private fun portal(
        values: Map<String, Any>,
        playerService: PlatformPlayerService = EmptyPlayerService
    ): RenderPortal {
        val base = File(System.getProperty("java.io.tmpdir"), "plumbot-e2e").apply { mkdirs() }
        return ImageRenderManager(
            context = FakeContext(FakeConfig(values), base.toPath(), playerService),
            libraryLoader = null,
            runtimeWebDir = base
        )
    }

    private fun render(portal: RenderPortal, route: String, payload: Any): RenderResult =
        runBlocking { portal.render(RenderRequest(route, payload)) }

    private fun decode(png: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(png))

    /** 生成一小段可用的 PNG（用于模拟服务端预取好的头像）。 */
    private fun tinyPngBytes(): ByteArray {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, 0xFF3A4250.toInt())
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    /** 同上，包装成 data URL（模拟载荷里服务端已预取好的头像字段）。 */
    private fun tinyPngDataUrl(): String =
        "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(tinyPngBytes())

    // ---------------- 用例 ----------------

    @Test
    fun `状态卡端到端产出可解码 PNG`() {
        val result = render(
            portal(mapOf("render.outputs.status" to true)),
            RenderRoute.STATUS.route,
            mapOf("serverName" to "lobby", "tps" to listOf("20.0", "19.9"), "mspt" to "12.4", "usedMem" to 512, "maxMem" to 4096, "online" to 7)
        )

        val png = assertIs<RenderResult.Image>(result).png
        assertTrue(png.size > 8, "产出不应为空")
        // PNG 8 字节签名
        assertEquals(0x89.toByte(), png[0])
        assertEquals('P'.code.toByte(), png[1])

        val image = decode(png)
        assertTrue(image.width > 0 && image.height > 0, "尺寸必须为正")
    }

    @Test
    fun `reload rebuilds engines and rendering still works afterwards`() {
        // A14：reload 会**关闭并丢弃**引擎与头像管线（让它们按新配置重建）。
        // 这条用例守的正是那次改动的风险：重建后必须仍能出图，而不是变成 Failed/Unavailable。
        val portal = portal(mapOf("render.outputs.status" to true))
        val payload = mapOf("serverName" to "lobby", "tps" to listOf("20.0"), "mspt" to "1", "usedMem" to 1, "maxMem" to 2, "online" to 1)

        val before = render(portal, RenderRoute.STATUS.route, payload)
        assertIs<RenderResult.Image>(before)

        portal.reload()

        val after = render(portal, RenderRoute.STATUS.route, payload)
        val png = assertIs<RenderResult.Image>(after, "reload 后引擎必须按新配置重建并继续出图").png
        assertTrue(decode(png).width > 0, "重建后的产物必须仍是可解码图片")

        // 二次 reload 也应幂等可用（避免"重载一次就坏"）
        portal.reload()
        assertIs<RenderResult.Image>(render(portal, RenderRoute.STATUS.route, payload))
    }

    @Test
    fun `玩家列表卡（含服务端预取头像）端到端产出 PNG`() {
        val result = render(
            portal(mapOf("render.outputs.list" to true)),
            RenderRoute.PLAYER_LIST.route,
            mapOf(
                "total" to 2,
                "players" to listOf(
                    mapOf("name" to "Steve", "boundQq" to null, "avatarUrl" to tinyPngDataUrl()),
                    mapOf("name" to "Alex", "boundQq" to "10001", "avatarUrl" to null)
                )
            )
        )

        val png = assertIs<RenderResult.Image>(result).png
        assertTrue(decode(png).width > 0)
    }

    @Test
    fun `超出额度的载荷经治理后仍能出图而不是崩溃`() {
        // 远超 displayTextMax（1024）的正文
        val huge = "夜".repeat(50_000)
        val result = render(portal(emptyMap()), RenderRoute.TEXT.route, TextPayload(body = huge))

        val png = assertIs<RenderResult.Image>(result).png
        assertTrue(decode(png).width > 0, "治理后必须仍能出图")
    }

    @Test
    fun `嵌套结构中的超长文本也会被治理`() {
        val nested = mapOf(
            "outer" to mapOf(
                "inner" to listOf(mapOf("deep" to "很长的文本".repeat(500)))
            )
        )
        val result = render(
            portal(mapOf("render.outputs.kick" to true)),
            RenderRoute.KICK.route,
            nested
        )
        // 旧实现只处理顶层，嵌套超长文本会直接进入渲染并可能耗尽内存
        val png = assertIs<RenderResult.Image>(result).png
        assertTrue(decode(png).width > 0)
    }

    @Test
    fun `门控关闭时不渲染（端到端降级路径）`() {
        val result = render(portal(emptyMap()), RenderRoute.STATUS.route, mapOf("serverName" to "x"))
        assertEquals(RenderResult.Unavailable, result)
    }

    @Test
    fun `引擎为 none 时全局短路`() {
        val result = render(
            portal(mapOf("render.engine" to "none")),
            RenderRoute.TEXT.route,
            TextPayload(body = "x")
        )
        assertEquals(RenderResult.Unavailable, result)
    }

    @Test
    fun `连续多次渲染互不影响且尺寸稳定`() {
        val p = portal(mapOf("render.outputs.status" to true))
        val payload = mapOf("serverName" to "s", "online" to 1)

        val sizes = (1..5).map {
            val png = assertIs<RenderResult.Image>(render(p, RenderRoute.STATUS.route, payload)).png
            decode(png).width to decode(png).height
        }
        assertEquals(1, sizes.toSet().size, "相同载荷的出图尺寸应稳定：$sizes")
    }

    @Test
    fun `同一目标并发渲染不会互相破坏`() = runBlocking {
        val p = portal(mapOf("render.outputs.status" to true))
        val results = (1..6).map { index ->
            async(Dispatchers.Default) {
                p.render(
                    RenderRequest(
                        route = RenderRoute.STATUS.route,
                        payload = mapOf("serverName" to "s$index", "online" to index),
                        targetKey = "group:1"
                    )
                )
            }
        }.map { it.await() }

        results.forEach { assertIs<RenderResult.Image>(it) }
    }

    // ---------------- 漏斗三入口的门户级测试（此前零覆盖） ----------------

    /**
     * **U-6 回归守卫**：熔断必须跨调用存活。
     *
     * 修复前 `resolveSkinAvatar` 每次调用都新建 `AvatarResolver`，连带新建空的冷却表，
     * 于是第二次解析仍会跑满整条漏斗 —— 本断言就会失败（pluginCalls 会变成 2）。
     */
    @Test
    fun `漏斗全灭后进入冷却 第二次解析不再调用任何平台来源`() = runBlocking {
        val player = CountingPlayerService()
        val p = portal(emptyMap(), player)

        assertNull(p.resolveSkinAvatar("Steve"), "三级全灭应返回 null")
        assertEquals(1, player.pluginCalls.get(), "第一次应真正走完漏斗")

        assertNull(p.resolveSkinAvatar("Steve"), "冷却期内仍返回 null")
        assertEquals(1, player.pluginCalls.get(), "冷却期内**不得**再次调用平台来源（修复前此处为 2）")
    }

    /** 负向对照：禁用熔断后每次都应重试，证明上一个用例的短路确实来自熔断而非其它原因。 */
    @Test
    fun `冷却设为禁用时每次解析都会重试`() = runBlocking {
        val player = CountingPlayerService()
        val p = portal(mapOf("render.avatar.failure_cooldown_minutes" to -1), player)

        p.resolveSkinAvatar("Steve")
        p.resolveSkinAvatar("Steve")
        assertEquals(2, player.pluginCalls.get(), "禁用熔断后应每次都重试")
    }

    /** `reload()` 会重建解析器，因此熔断状态一并重置（配置变更后允许重试）。 */
    @Test
    fun `reload 会重置熔断状态`() = runBlocking {
        val player = CountingPlayerService()
        val p = portal(emptyMap(), player)

        p.resolveSkinAvatar("Steve")
        assertEquals(1, player.pluginCalls.get())

        p.reload()
        p.resolveSkinAvatar("Steve")
        assertEquals(2, player.pluginCalls.get(), "reload 后应允许重新尝试")
    }

    /** `composeAvatar` 门户入口：合成 + 磁盘缓存。 */
    @Test
    fun `composeAvatar 经门户合成并写入缓存`() = runBlocking {
        val p = portal(emptyMap())
        val request = AvatarComposeRequest(
            cacheKey = "unit-test:compose",
            layers = listOf(AvatarLayer(tinyPngBytes(), AvatarBlend.NORMAL))
        )

        val first = assertNotNull(p.composeAvatar(request), "有效图层应能合成")
        assertTrue(first.startsWith("data:image/png;base64,"), "应返回 data URL：$first")

        val second = assertNotNull(p.composeAvatar(request))
        assertEquals(first, second, "第二次应命中缓存并返回一致结果")

        // 空图层是"无可用头像"的信号，必须返回 null 让上层回退
        assertNull(p.composeAvatar(AvatarComposeRequest(cacheKey = null, layers = emptyList())))
    }

    /**
     * `fetchAvatarAsDataUrl` 门户入口的**拒绝路径**（可离线判定）。
     *
     * `AvatarFetcher.download` 先判 scheme、再判主机白名单，二者失败都**早于** DNS 解析，
     * 因此这两个断言不产生任何网络访问。
     */
    @Test
    fun `fetchAvatarAsDataUrl 拒绝非 https 与非白名单来源`() = runBlocking {
        val p = portal(emptyMap())

        assertNull(
            p.fetchAvatarAsDataUrl("k1", "http://q1.qlogo.cn/g?b=qq&nk=1&s=640"),
            "明文 http 必须拒绝"
        )
        assertNull(
            p.fetchAvatarAsDataUrl("k2", "https://evil.example/skin.png"),
            "非白名单主机必须拒绝"
        )
    }

    /**
     * 门控真值表：
     * ① 有门控的路由，`render.outputs.<功能>=false` 必须一律不可用；
     * ② 同一开关为 true 时必须放行（调用方只表达"要图片"，判门控只发生在门户）；
     * ③ 多个路由共用一个功能开关时（如 whitelist/bindList 共用 `render.outputs.bind`），
     *    该开关对每条路由都生效。
     *
     * ②③ 只在代表性路由上断言，避免 15 条路由各跑一次完整渲染；① 的断言在门控处短路，代价极低。
     */
    @Test
    fun `门控真值表覆盖全部路由`() {
        val gated = RenderRoute.entries.filter { it.featureGate != null }
        val gateless = RenderRoute.entries.filter { it.featureGate == null }
        assertTrue(gated.size == 15 && gateless.size == 1, "路由表形态变化时本用例需要同步更新")

        gated.forEach { route ->
            val off = render(
                portal(mapOf("render.outputs.${route.featureGate}" to false)),
                route.route,
                mapOf("serverName" to "x")
            )
            assertEquals(RenderResult.Unavailable, off, "${route.route}: 功能开关关闭必须不可用")
        }

        listOf(RenderRoute.STATUS, RenderRoute.KICK, RenderRoute.WHITELIST).forEach { route ->
            val on = render(
                portal(mapOf("render.outputs.${route.featureGate}" to true)),
                route.route,
                mapOf("serverName" to "x")
            )
            assertNotEquals(RenderResult.Unavailable, on, "${route.route}: 功能开关开启不得被门控拦下")
        }

        val sharedGate = render(
            portal(mapOf("render.outputs.bind" to true)),
            RenderRoute.BIND_LIST.route,
            mapOf("serverName" to "x")
        )
        assertNotEquals(RenderResult.Unavailable, sharedGate, "共用 render.outputs.bind 的路由必须一起放行")

        gateless.forEach { route ->
            val result = render(portal(emptyMap()), route.route, TextPayload(body = "x"))
            assertNotEquals(RenderResult.Unavailable, result, "${route.route} 没有独立门控，不该被门控拦下")
        }
    }

    /**
     * 计数分离的前提（A23）：`isImageEnabled` 只回答"配置有没有启用图片"。
     *
     * 若把 `Unavailable` 的其他原因（队列溢出、引擎不可用）也算成"关闭"，
     * `/plumbot status` 里的"配置关闭"会虚高，而真正的降级故障被掩盖。
     */
    @Test
    fun `isImageEnabled 只反映配置门控与 engine none`() {
        RenderRoute.entries.filter { it.featureGate != null }.forEach { route ->
            assertEquals(
                false,
                portal(mapOf("render.outputs.${route.featureGate}" to false)).isImageEnabled(route.route),
                "${route.route}: 功能开关关闭应报告未启用",
            )
            assertEquals(
                true,
                portal(mapOf("render.outputs.${route.featureGate}" to true)).isImageEnabled(route.route),
                "${route.route}: 功能开关开启应报告启用",
            )
        }
        assertEquals(false, portal(mapOf("render.engine" to "none")).isImageEnabled("status"), "engine=none 等于主动关闭")
        assertEquals(true, portal(emptyMap()).isImageEnabled("text"), "无门控路由默认启用")
        assertEquals(false, portal(emptyMap()).isImageEnabled("noSuchRoute"), "未知路由无法出图")
    }
}
