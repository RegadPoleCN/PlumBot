// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.assets

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.AvatarBlend
import me.regadpole.plumbot.render.AvatarComposeRequest
import me.regadpole.plumbot.render.AvatarLayer
import net.kyori.adventure.text.Component

/**
 * 头像缓存键契约：合成结果依赖 request.size（画布尺寸）与 head_size（漏斗裁切）两者，
 * 键里缺了任何一个，同 key 不同尺寸的请求就会互相命中错误尺寸的成品。
 */
class AvatarServiceSizeKeyTest {

    @Test
    fun `同一 cacheKey 不同 size 各自命中各自缓存`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("plumbot-avatar-size")
        val service = AvatarService(TestContext(), dir.resolve("web").toFile(), Any()) { null }
        val layers = listOf(AvatarLayer(tinyPng(), AvatarBlend.NORMAL))

        val large = assertNotNull(service.composeAvatar(AvatarComposeRequest("unit:size", layers, 128)))
        val small = assertNotNull(service.composeAvatar(AvatarComposeRequest("unit:size", layers, 64)))
        assertNotEquals(large, small, "不同 size 必须是不同缓存条目，不能互相命中")

        val again = assertNotNull(service.composeAvatar(AvatarComposeRequest("unit:size", layers, 128)))
        assertEquals(large, again, "同 size 第二次应命中缓存")

        val cacheDir = dir.resolve("cache/avatars").toFile()
        assertEquals(2, cacheDir.listFiles()?.count { it.name.endsWith(".png") } ?: 0,
            "两个 size 应落两个缓存文件")
        dir.toFile().deleteRecursively()
    }

    private fun tinyPng(): ByteArray {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, 0xFF3A4250.toInt())
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private class TestContext : PlatformContext {
        override val config: PlatformConfig = EmptyConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: java.nio.file.Path = Files.createTempDirectory("plumbot-avatar-size-ctx")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("头像缓存测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private class EmptyConfig : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }
}