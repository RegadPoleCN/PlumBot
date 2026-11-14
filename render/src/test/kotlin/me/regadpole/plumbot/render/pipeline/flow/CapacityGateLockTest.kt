// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.pipeline.flow

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformConfig
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformPlayerService
import me.regadpole.plumbot.api.platform.PlatformScheduler
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.TextPayload
import net.kyori.adventure.text.Component

/**
 * 目标锁驱逐的回归护栏：等待者必须能保住自己的条目，串行性不得被水位驱逐破坏。
 * 旧实现只看 mutex.isLocked，刚入表还没 lock 的等待者会被误判为空闲而遭驱逐。
 */
class CapacityGateLockTest {

    private class TestConfig : PlatformConfig {
        override fun getString(vararg path: String?): String? = null
        override fun getLong(vararg path: String?): Long = 0L
        override fun getInteger(vararg path: String?): Int = 0
        override fun getBoolean(vararg path: String?): Boolean = false
        override fun getLongList(vararg path: String?): List<Long> = emptyList()
        override fun getStringList(vararg path: String?): List<String?> = emptyList()
        override fun getSubConfig(vararg path: String?): PlatformConfig = this
    }

    private class TestContext : PlatformContext {
        override val config = TestConfig()
        override val datasource: PlatformConfig get() = config
        override val dataDirectory: Path = java.nio.file.Files.createTempDirectory("plumbot-gate-test")
        override val scheduler: PlatformScheduler = object : PlatformScheduler {
            override fun runSync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runAsync(task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLater(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runLaterAsync(delay: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeating(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
            override fun runRepeatingAsync(initialDelay: kotlin.time.Duration, period: kotlin.time.Duration, task: () -> Unit) = PlatformTaskHandle { true }
        }
        override val playerService: PlatformPlayerService get() = TODO("容量闸测试未使用玩家服务")
        override val platformType: PlatformType = PlatformType.BUKKIT
        override fun log(level: LogLevel, message: String) = Unit
        override fun sendMessage(message: Component) = Unit
        override fun isPluginAvailable(name: String): Boolean = false
    }

    private fun request(targetKey: String?) =
        RenderRequest(route = "status", payload = TextPayload("body"), targetKey = targetKey)

    @Test
    fun `持锁目标在水位驱逐中被保留且串行性不被破坏`() = runBlocking {
        val gate = CapacityGate(TestContext())
        val order = java.util.Collections.synchronizedList(mutableListOf<String>())
        val releaseFirst = CompletableDeferred<Unit>()

        val first = launch {
            gate.dispatch(RenderRoute.STATUS, "test", request("group:1")) {
                order += "first-enter"
                releaseFirst.await()
                order += "first-exit"
                RenderResult.Image(ByteArray(0))
            }
        }
        while (order.size < 1) yield()

        // 填满水位触发多轮驱逐（每个 dispatch 的 finally 都会调用 evictIdleLocks）：
        // 持锁的 group:1（users=1）必须在每一轮驱逐中幸存。
        repeat(520) { index ->
            gate.dispatch(RenderRoute.STATUS, "test", request("idle:" + index)) {
                RenderResult.Image(ByteArray(0))
            }
        }

        val secondEntered = CompletableDeferred<Unit>()
        val second = launch {
            gate.dispatch(RenderRoute.STATUS, "test", request("group:1")) {
                order += "second-enter"
                secondEntered.complete(Unit)
                RenderResult.Image(ByteArray(0))
            }
        }
        val blocked = withTimeoutOrNull(400) { secondEntered.await() }
        assertTrue(blocked == null, "第二条渲染必须被同目标锁挡住：串行性未被驱逐破坏")

        releaseFirst.complete(Unit)
        first.join()
        second.join()
        assertEquals(listOf("first-enter", "first-exit", "second-enter"), order.toList(), "同目标必须严格串行")
        gate.clear()
    }

    @Test
    fun `空闲目标在超过水位后被驱逐`() = runBlocking {
        val gate = CapacityGate(TestContext())
        repeat(600) { index ->
            gate.dispatch(RenderRoute.STATUS, "test", request("idle:" + index)) {
                RenderResult.Image(ByteArray(0))
            }
        }
        assertTrue(gate.trackedTargetCount() <= 513, "水位驱逐应生效，实际跟踪 " + gate.trackedTargetCount() + " 个目标")
        gate.clear()
    }
}