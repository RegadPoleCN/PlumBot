// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.theme

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component

/**
 * 模板下载失败负缓存契约：失败后一段时间内直接用上次原因回答，不再每次渲染都撞一次下载。
 * NEGATIVE_CACHE_TTL_MS 是 ThemeManager 的私有常量（当前实现 60s），测试用 61s 越过它。
 * 候选指向本机必然拒绝的地址，保证"下载失败"确定性成立且不依赖外网。
 */
class ThemeManagerNegativeCacheTest {

    private var now = 0L

    private fun manager(): ThemeManager =
        ThemeManager(TestContext(), Files.createTempDirectory("plumbot-theme-test").toFile(), clock = { now })

    @Test
    fun `下载失败后 60 秒内不再发起下载`() = runBlocking {
        val manager = manager()
        val first = manager.resolveTheme("default", "status")
        assertTrue(first is ThemeResolution.Failed, "候选全部不可达时必须失败")
        assertTrue(first.detail.contains("模板下载失败"), first.detail)

        now += 5_000
        val second = manager.resolveTheme("default", "status")
        assertTrue(second is ThemeResolution.Failed, "负缓存命中同样必须给 Failed")
        assertTrue(second.detail.contains("负缓存"), "第二次失败必须来自负缓存而不是再次下载：${second.detail}")
        assertTrue(second.detail.contains("模板下载失败"), "负缓存回答必须带上上次失败原因：${second.detail}")
    }

    @Test
    fun `负缓存过期后恢复真实下载尝试`() = runBlocking {
        val manager = manager()
        manager.resolveTheme("default", "status")

        now += 61_000L // 越过 60s 负缓存 TTL
        val third = manager.resolveTheme("default", "status")
        assertTrue(
            third is ThemeResolution.Failed && third.detail.contains("模板下载失败"),
            "负缓存过期后必须重新进入下载路径，而不是永远用缓存回答：${(third as? ThemeResolution.Failed)?.detail}"
        )
    }

    private class TestContext : PlatformContext {
        override val config = TestConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: java.nio.file.Path = Files.createTempDirectory("plumbot-theme-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("主题测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class TestConfig : PlatformConfig {
        private val values = mapOf<String, Any?>(
            "render.playwright.template_urls" to listOf("https://127.0.0.1:9/index.html"),
        )

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