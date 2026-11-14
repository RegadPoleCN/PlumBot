// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.platform.PlatformType
import me.regadpole.plumbot.task.TaskProviderImpl
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakeBotFactory
import me.regadpole.plumbot.testing.FakePlatformContext
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bot 生命周期与注册表的**并发**契约：
 * 1. 加载失败时只释放本次新建的实例——旧实现误用旧字段值，把仍在服务的旧实例关掉，
 *    真正泄漏的半初始化新实例反而无人释放；
 * 2. 注册表的读-判-删必须原子，读写并发不得破坏内部映射；
 * 3. 任务作用域 shutdown 后要能重建（平台"禁用→再次启用"），Error 不得让 Future 永不完成；
 * 4. 并发确认在线时 load 广播必须恰好一次。
 */
class BotProviderLifecycleTest {

    @AfterTest
    fun cleanup() {
        BotProvider.unloadBot()
        BotProvider.clearFactories()
    }

    @Test
    fun `failed start releases the fresh instance and leaves the old one untouched`() {
        val context = FakePlatformContext()
        val old = FakeBot(context, type = "fake-old")
        BotProvider.registerFactory(FakeBotFactory(old))
        BotProvider.loadBot(context, "fake-old")
        assertFalse(old.doShutdownCalled, "前提：旧实例应处于服务中")

        val fresh = FakeBot(context, type = "fake-fresh")
        fresh.failOnStart = IllegalStateException("boom")
        BotProvider.registerFactory(FakeBotFactory(fresh))
        assertFailsWith<IllegalStateException> { BotProvider.loadBot(context, "fake-fresh") }

        assertTrue(fresh.doShutdownCalled, "加载失败必须释放半初始化的新实例（连接/协程作用域）")
        assertFalse(old.doShutdownCalled, "失败清理不得误关仍在服务的旧实例")
        assertNull(BotProvider.getBot(), "失败后不得留下指向失败实例的引用")
    }

    @Test
    fun `registry tolerates concurrent register unregister and reads`() {
        val registry = BotRegistry()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (0 until 16).map { i ->
            Thread {
                try {
                    repeat(200) { j ->
                        val type = "t-" + ((i * 7 + j) % 32)
                        val factory = FakeBotFactory(FakeBot(type = type))
                        registry.register(factory)
                        registry.find(type)
                        registry.find(null)
                        registry.factories()
                        registry.registeredTypes()
                        registry.compatibleFactories(PlatformType.BUKKIT)
                        registry.unregister(type)
                        assertFalse(registry.unregister("never-registered-" + j), "注销不存在的键必须返回 false")
                    }
                } catch (t: Throwable) {
                    errors += t
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        assertTrue(errors.isEmpty(), "并发读写不得抛异常：" + errors.firstOrNull()?.message)
    }

    @Test
    fun `errors thrown in submitted tasks complete the future exceptionally`() {
        val future = TaskProviderImpl.submit(Runnable { throw OutOfMemoryError("boom") })
        val ex = assertFailsWith<ExecutionException> { future.get(5, TimeUnit.SECONDS) }
        assertTrue(ex.cause is OutOfMemoryError, "Error 必须进入 Future 而不是让 get() 永久挂起")
    }

    @Test
    fun `scopes are rebuilt after shutdown`() {
        TaskProviderImpl.shutdown()
        val ran = AtomicBoolean(false)
        val future = TaskProviderImpl.submit(Runnable { ran.set(true) })
        future.get(5, TimeUnit.SECONDS)
        assertTrue(ran.get(), "shutdown 后 submit 必须重建作用域并执行任务")

        // 重复 shutdown 不得抛错（平台可能多次触发禁用）
        TaskProviderImpl.shutdown()
        TaskProviderImpl.shutdown()
    }

    @Test
    fun `concurrent markOnline announces the load broadcast exactly once`() {
        val context = FakePlatformContext(
            mapOf(
                "feature.load.enable" to true,
                "groups" to listOf(100L),
            )
        )
        val bot = FakeBot(context)
        bot.onlineAfterStart = false
        bot.start()

        val barrier = CyclicBarrier(16)
        val threads = (1..16).map {
            Thread {
                runCatching { barrier.await() }
                bot.markOnlineForTest()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5_000) }
        assertEquals(1, bot.sentMessages.size, "并发确认在线时 load 广播必须恰好一次")
    }
}