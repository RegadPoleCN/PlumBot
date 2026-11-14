// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.adapter.onebot

import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import net.kyori.adventure.text.Component
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

/** 未连接的 [WebsocketBotClient]：构造只建对象不连接，action() 在 client 为空时抛 IllegalStateException。 */
private class StubConfig : PlatformConfig {
    override fun getString(vararg path: String?): String? = null
    override fun getLong(vararg path: String?): Long = 0L
    override fun getInteger(vararg path: String?): Int = 0
    override fun getBoolean(vararg path: String?): Boolean = false
    override fun getLongList(vararg path: String?): List<Long> = emptyList()
    override fun getStringList(vararg path: String?): List<String?> = emptyList()
    override fun getSubConfig(vararg path: String?): PlatformConfig = this
}

private class StubScheduler : PlatformScheduler {
    private val handle = PlatformTaskHandle { true }
    override fun runSync(task: () -> Unit): PlatformTaskHandle { task(); return handle }
    override fun runAsync(task: () -> Unit): PlatformTaskHandle { task(); return handle }
    override fun runLater(delay: Duration, task: () -> Unit): PlatformTaskHandle = handle
    override fun runLaterAsync(delay: Duration, task: () -> Unit): PlatformTaskHandle = handle
    override fun runRepeating(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle = handle
    override fun runRepeatingAsync(initialDelay: Duration, period: Duration, task: () -> Unit): PlatformTaskHandle = handle
}

private class StubPlayers : PlatformPlayerService {
    override fun kickPlayer(name: String) {}
    override fun listPlayers(): List<String> = emptyList()
    override fun listPlayerString(): String = ""
}

private class RecordingContext : PlatformContext {
    val warnings = ConcurrentLinkedQueue<String>()
    override val config: PlatformConfig = StubConfig()
    override val datasource: PlatformConfig = StubConfig()
    override val dataDirectory = Files.createTempDirectory("plumbot-onebot-test")
    override val scheduler: PlatformScheduler = StubScheduler()
    override val playerService: PlatformPlayerService = StubPlayers()
    override val platformType: PlatformType = PlatformType.STANDALONE
    override fun log(level: LogLevel, message: String) {
        if (level == LogLevel.WARN) warnings.add(message)
    }
    override fun sendMessage(message: Component) {}
    override fun isPluginAvailable(name: String): Boolean = false
}

private fun newAdapter(context: RecordingContext): OneBotAdapter =
    OneBotAdapter(context, WebsocketBotClient("127.0.0.1", 0), BotAdapterMetadata(type = "onebot", displayName = "OneBot"))

class OneBotAdapterSendTest {

    @Test
    fun textSendsAreLoggedInsteadOfThrownWhileWebsocketDown() {
        val ctx = RecordingContext()
        val adapter = newAdapter(ctx)

        // 断连窗口：四个文本方法都不得向调用方抛异常（渲染/事件链路无从兜底）。
        adapter.sendGroupMsg(123L, "hello")
        adapter.sendUserMsg(456L, "hello")
        adapter.sendGroupMsgAt(123L, 789L, "hi")
        adapter.sendGroupMsgAtAll(123L, "hi")

        assertEquals(4, ctx.warnings.size, "每次失败的发送都应记一条 WARN，实际：" + ctx.warnings)
        assertTrue(ctx.warnings.any { it.contains("群消息发送失败") })
        assertTrue(ctx.warnings.any { it.contains("私聊消息发送失败") })
    }

    @Test
    fun imageSendKeepsReturningFalseWhileWebsocketDown() {
        val ctx = RecordingContext()
        val adapter = newAdapter(ctx)

        // 图片路径早已有 catch Throwable → false + WARN 的契约，回归确认未被文本改动波及。
        assertFalse(adapter.sendGroupImage(123L, ByteArray(0)))
        assertFalse(adapter.sendUserImage(456L, ByteArray(0)))
    }

    @Test
    fun generatedEchoIdsStayUniqueUnderConcurrency() {
        val seen = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        val duplicates = AtomicInteger(0)
        val threads = (1..8).map {
            Thread {
                repeat(250) {
                    if (!seen.add(OneBotEchoGenerator.next())) duplicates.incrementAndGet()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        assertEquals(0, duplicates.get())
        assertEquals(2000, seen.size)
    }
}