// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.api.database.Binding
import me.regadpole.plumbot.api.database.IDatabase
import me.regadpole.plumbot.config.YamlConfigurator
import taboolib.module.database.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 登录白名单查询层（fail-closed）的三条不变式：
 * 缓存命中不打库、同名并发只查一次、等待有上限且超时折成 UNAVAILABLE。
 *
 * 用真实 SQLite + 委托包装（只覆写 getByName）来数查询次数，而不是 mock ——
 * 这样「命中缓存后到底有没有再发 SQL」是被观察到的，不是被假设的。
 */
class BindingLookupServiceTest {

    private val tempDir: Path = Files.createTempDirectory("plumbot-lookup-test")
    private val databases = mutableListOf<IDatabase>()
    private var fakeNow = 1_000_000L

    /** 只覆写 getByName 的计数包装；其余方法一律委托给真实 SQLite。 */
    private class CountingDatabase(
        private val delegate: IDatabase,
        private val delayMillis: Long,
    ) : IDatabase by delegate {

        val calls = AtomicInteger()

        override fun getByName(name: String): Binding? {
            calls.incrementAndGet()
            if (delayMillis > 0) Thread.sleep(delayMillis)
            return delegate.getByName(name)
        }
    }

    @BeforeTest
    fun setUp() {
        fakeNow = 1_000_000L
        BindingLookupService.resetForTests()
        BindingLookupService.clock = { fakeNow }
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        BindingLookupService.resetForTests()
        runCatching {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private fun start(delayMillis: Long = 0L): CountingDatabase {
        if (!settingsInitialized) {
            val config = YamlConfigurator.createConfig(tempDir, "datasource.yml")
                ?: error("测试无法加载 datasource.yml")
            Database.settingsFile = DatabaseSource(config.getNode())
            settingsInitialized = true
        }
        val sqlite = SQLite(tempDir.resolve("binding.db").absolutePathString())
        databases += sqlite
        val counting = CountingDatabase(sqlite, delayMillis)
        DatabaseProvider.start(counting)
        return counting
    }

    private fun outcome(playerName: String, ttlSeconds: Long = 300L, timeoutMs: Long = 500L) =
        BindingLookupService.resolve(playerName, ttlSeconds, timeoutMs)

    private fun awaitCalls(database: CountingDatabase, expected: Int) {
        val deadline = System.currentTimeMillis() + 2_000L
        while (System.currentTimeMillis() < deadline && database.calls.get() < expected) Thread.sleep(10L)
        assertTrue(database.calls.get() >= expected, "后台刷新没有发生：calls=${database.calls.get()}")
    }

    @Test
    fun `a cached binding is served without a second query`() {
        val counting = start()
        counting.addBind("10001", "Steve")

        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)
        assertEquals(1, counting.calls.get())

        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)
        assertEquals(1, counting.calls.get(), "缓存命中不应再查数据库")
    }

    @Test
    fun `a confirmation that the player is not bound is cached negatively`() {
        val counting = start()

        assertEquals(BindingLookupService.Outcome.NOT_BOUND, outcome("Alex").outcome)
        assertEquals(BindingLookupService.Outcome.NOT_BOUND, outcome("Alex").outcome)
        assertEquals(1, counting.calls.get(), "负缓存（30s）内的重复登录不应再查库")

        fakeNow += BindingLookupService.NEGATIVE_TTL_MS + 1_000L
        assertEquals(BindingLookupService.Outcome.NOT_BOUND, outcome("Alex").outcome)
        assertEquals(2, counting.calls.get(), "负缓存过期后必须重新查库")
    }

    @Test
    fun `adding a binding invalidates a cached negative result`() {
        val counting = start()
        assertEquals(BindingLookupService.Outcome.NOT_BOUND, outcome("Steve").outcome)

        counting.addBind("10001", "Steve")

        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome, "写后必须立即生效")
    }

    @Test
    fun `removing a binding invalidates a cached positive result`() {
        val counting = start()
        counting.addBind("10001", "Steve")
        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)

        counting.removeBind("Steve")

        assertEquals(BindingLookupService.Outcome.NOT_BOUND, outcome("Steve").outcome)
    }

    @Test
    fun `concurrent logins for one name issue a single query`() {
        val counting = start(delayMillis = 150L)
        counting.addBind("10001", "Steve")

        val workers = 8
        val startLatch = CountDownLatch(1)
        val outcomes = Collections.synchronizedList(mutableListOf<BindingLookupService.Outcome>())
        val threads = (1..workers).map {
            thread {
                startLatch.await()
                outcomes += outcome("Steve", timeoutMs = 5_000L).outcome
            }
        }
        startLatch.countDown()
        threads.forEach { it.join(10_000L) }

        assertEquals(workers, outcomes.size)
        assertTrue(outcomes.all { it == BindingLookupService.Outcome.BOUND }, "并发登录都应放行：$outcomes")
        assertEquals(1, counting.calls.get(), "同名并发只允许一次数据库查询")
    }

    @Test
    fun `a query slower than the timeout is reported unavailable`() {
        start(delayMillis = 400L)

        val result = outcome("Steve", timeoutMs = 50L)

        assertEquals(BindingLookupService.Outcome.UNAVAILABLE, result.outcome)
        assertTrue(result.reason!!.contains("超过"), "原因要能指向超时：${result.reason}")
    }

    @Test
    fun `a database that is not ready is reported unavailable`() {
        DatabaseProvider.shutdown()

        val result = outcome("Steve")

        assertEquals(BindingLookupService.Outcome.UNAVAILABLE, result.outcome)
        assertTrue(result.reason!!.contains("尚未就绪"), "未就绪的异常要折成可读原因：${result.reason}")
    }

    @Test
    fun `a stale positive entry is reused and refreshed in the background`() {
        val counting = start()
        counting.addBind("10001", "Steve")
        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)

        fakeNow += 301_000L

        assertEquals(
            BindingLookupService.Outcome.BOUND,
            outcome("Steve").outcome,
            "过期但在陈旧窗口内，数据库抖动时不能让已绑定玩家进不来"
        )
        awaitCalls(counting, 2)

        fakeNow += 5_000L
        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)
        assertEquals(2, counting.calls.get(), "刷新后的条目在 TTL 内不应再查库")
    }

    @Test
    fun `the lookup service is usable again after a database restart`() {
        val counting = start()
        counting.addBind("10001", "Steve")
        assertEquals(BindingLookupService.Outcome.BOUND, outcome("Steve").outcome)

        // 模拟 reload：shutdown 关掉实例并取消了查询作用域；start 用新实例接管同一个库文件，
        // 之后必须能重新查库（作用域不重建就只能等满超时）。
        DatabaseProvider.shutdown()
        start()

        assertEquals(
            BindingLookupService.Outcome.BOUND,
            outcome("Steve").outcome,
            "重载之后不能因为作用域被取消就永远超时"
        )
    }

    private companion object {
        var settingsInitialized = false
    }
}
