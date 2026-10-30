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

package me.regadpole.plumbot.database

import me.regadpole.config.DatabaseSource
import me.regadpole.plumbot.config.YamlConfigurator
import taboolib.module.database.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **并发写入同一角色名，只允许产生一条绑定**。
 *
 * "先查后插"的经典竞态：两个线程同时判断"不存在"，再各插一条。实现靠**唯一索引兜底**
 * （冲突折叠为"已存在"），单线程测试跑多少次都验证不了它是否成立，所以用真实 SQLite
 * 而非替身：唯一索引与驱动行为正是被测对象。
 */
class BindingConcurrencyTest {

    private val tempDir: Path = Files.createTempDirectory("plumbot-binding-concurrency")
    private val databases = mutableListOf<AbstractBindingDatabase<*, *>>()

    private fun ensureSettingsFile() {
        if (!settingsInitialized) {
            val config = YamlConfigurator.createConfig(tempDir, "datasource.yml")
                ?: error("测试无法加载 datasource.yml")
            Database.settingsFile = DatabaseSource(config.getNode())
            settingsInitialized = true
        }
    }

    private fun newDatabase(): AbstractBindingDatabase<*, *> {
        ensureSettingsFile()
        val db = SQLite(tempDir.resolve("concurrency.db").absolutePathString())
        db.initialize()
        databases += db
        return db
    }

    @AfterTest
    fun cleanup() {
        databases.forEach { runCatching { it.close() } }
        databases.clear()
        runCatching {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private companion object {
        var settingsInitialized = false
    }

    @Test
    fun `fifty concurrent writes of the same name leave exactly one binding`() {
        val database = newDatabase()
        val threads = 50
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)

        try {
            repeat(threads) { index ->
                pool.execute {
                    // 用不同 QQ 抢同一个角色名：竞争最激烈的情形
                    try {
                        start.await()
                        database.addBindIfAbsent("user-$index", "Steve")
                    } catch (t: Throwable) {
                        System.err.println("[test] 并发写入抛出异常：${t::class.simpleName}: ${t.message}")
                    } finally {
                        done.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue(done.await(60, TimeUnit.SECONDS), "50 个并发写入必须在 60 秒内完成")

            val rows = database.getByName("Steve")
            // 名字必须存在，且 50 个 QQ 加起来只对应一条记录（下面按 QQ 汇总）
            assertTrue(rows != null, "同名绑定必须存在（并发失败不应把记录全丢）")
            assertEquals(
                1,
                database.getByUser("user-0").size + database.getByUser("user-1").size + (2..49).sumOf { database.getByUser("user-$it").size },
                "50 个不同 QQ 抢同一角色名，最终只能有 1 条绑定记录"
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `counting bindings matches the rows actually stored`() {
        val database = newDatabase()

        assertEquals(0, database.countByUser("user-7"), "未绑定应计 0（而不是抛错或返回 null 语义）")

        database.addBindIfAbsent("user-7", "Alice")
        database.addBindIfAbsent("user-7", "Bob")
        database.addBind("user-8", "Carol")

        assertEquals(2, database.countByUser("user-7"), "计数必须与已写入行数一致")
        assertEquals(1, database.countByUser("user-8"), "不同 QQ 之间不得串号")
        assertEquals(2, database.countByUser("  user-7  "), "计数同样要归一化输入（与其它查询一致）")
    }

    @Test
    fun `the per user limit cannot be exceeded by concurrent writes`() {
        // 名额校验与写入必须在同一事务内：拆成"先 count 再 insert"两次往返时，并发请求
        // 都会读到"还差一个名额"，于是**突破上限**（同名冲突有唯一索引兜底，名额上限没有）。
        val database = newDatabase()
        val limit = 2
        val threads = 20
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val added = java.util.concurrent.atomic.AtomicInteger()

        try {
            repeat(threads) { index ->
                pool.execute {
                    try {
                        start.await()
                        // 同一个 QQ 抢 20 个不同角色名，上限 2
                        if (database.addBindIfAbsentWithinLimit("user-1", "Player$index", limit)) added.incrementAndGet()
                    } catch (_: Throwable) {
                        // 数据库繁忙等异常视为"未写入"，由下面的不变量断言兜底
                    } finally {
                        done.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue(done.await(60, TimeUnit.SECONDS), "并发写入必须在 60 秒内完成")

            val stored = database.countByUser("user-1")
            assertTrue(stored <= limit, "名额上限不得被并发突破（上限 $limit，实际 $stored 条）")
            assertEquals(stored, added.get(), "计数必须与本次成功写入数一致（否则存在未被计入的写入）")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent writes of distinct names all succeed`() {
        val database = newDatabase()
        val threads = 20
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = java.util.concurrent.atomic.AtomicInteger()

        try {
            repeat(threads) { index ->
                pool.execute {
                    try {
                        start.await()
                        if (!database.addBindIfAbsent("user-$index", "Player$index")) failures.incrementAndGet()
                    } catch (t: Throwable) {
                        failures.incrementAndGet()
                    } finally {
                        done.countDown()
                    }
                }
            }
            start.countDown()
            assertTrue(done.await(60, TimeUnit.SECONDS), "并发写入必须在 60 秒内完成")
            assertEquals(0, failures.get(), "不同角色名之间不应互相干扰（各自都应写入成功）")
        } finally {
            pool.shutdownNow()
        }
    }
}
