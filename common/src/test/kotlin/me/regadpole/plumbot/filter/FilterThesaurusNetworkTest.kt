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

package me.regadpole.plumbot.filter

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.testing.FakePlatformContext
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * **词库抓取的真实网络分支**（本地 HTTP 服务器）。
 *
 * 词库抓取走普通 `HttpURLConnection`，**不受** `SafeHttp` 的"仅 https + 拒绝内网/回环"约束，
 * 因此 `http://127.0.0.1:port` 是合法源地址，真实分支可被完整执行。覆盖：200 正常 JSON 生效、
 * 非 200 降级读历史快照、坏响应（HTML/空体）**不覆盖快照**。
 */
class FilterThesaurusNetworkTest {

    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun cleanup() {
        servers.forEach { runCatching { it.stop(0) } }
        servers.clear()
        // HealthCounters 是**全局单例**：本测试的 process() 会累加 FILTER_HITS 等计数，
        // 若不复原会污染同 JVM 内的其它测试（断言绝对值的那些会随机失败）。
        me.regadpole.plumbot.internal.HealthCounters.reset()
    }

    private fun serve(
        status: Int = 200,
        body: String = "{}",
        delayMs: Long = 0,
        declaredLength: Long? = null,
    ): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/thesaurus.json") { exchange ->
            if (delayMs > 0) Thread.sleep(delayMs)
            val bytes = body.toByteArray()
            // declaredLength 用于模拟"声明超大"的响应头：客户端应在**读入内存之前**就拒绝
            exchange.sendResponseHeaders(status, declaredLength ?: bytes.size.toLong())
            runCatching { exchange.responseBody.use { it.write(bytes) } }
        }
        server.start()
        servers += server
        return "http://127.0.0.1:${server.address.port}/thesaurus.json"
    }

    private fun contextWith(url: String, cacheDir: java.io.File): FakePlatformContext =
        FakePlatformContext(
            mapOf(
                "filter.enable" to true,
                "filter.action" to "REPLACE",
                "filter.cloud.enable" to true,
                "filter.cloud.urls" to listOf(url),
                "filter.cloud.cache_dir" to cacheDir.absolutePath,
                "filter.cloud.timeout_ms" to 2000,
                "filter.local_words" to emptyList<String>(),
                "filter.ignored_words" to emptyList<String>(),
            )
        )

    /** 与生产实现一致的快照文件名（`sha256(url).take(16)`）。 */
    private fun snapshotFile(cacheDir: java.io.File, url: String): java.io.File {
        val hex = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return java.io.File(cacheDir, "source_${hex.take(16)}.json")
    }

    @Test
    fun `a healthy source makes the words effective`() {
        val url = serve(body = """{"words":["坏词甲","badword"]}""")
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-ok").toFile()
        val manager = FilterThesaurusManager(contextWith(url, cacheDir))

        try {
            runBlocking { manager.reload() }

            val result = manager.process("这里包含坏词甲")
            assertTrue(result.matchedWords.isNotEmpty(), "抓取成功后云端词库必须生效（实际：${result.matchedWords}）")
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `a non 200 response falls back to the previous snapshot`() {
        val url = serve(status = 404, body = "not found")
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-404").toFile()
        // 预置历史快照：降级路径必须读取它，而不是把词库清空
        snapshotFile(cacheDir, url).writeText("""{"words":["历史坏词"]}""")

        val manager = FilterThesaurusManager(contextWith(url, cacheDir))
        try {
            runBlocking { manager.reload() }

            val result = manager.process("这里包含历史坏词")
            assertTrue(
                result.matchedWords.isNotEmpty(),
                "源不可用时应降级为上一次的快照（否则过滤会静默失效）"
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `a broken response does not overwrite the snapshot`() {
        // 图床/网关返回 HTML 错误页是常见情形：若把它当词库写入，离线降级这条退路也一起失效
        val url = serve(status = 200, body = "<html><body>502 Bad Gateway</body></html>")
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-bad").toFile()
        val snapshot = snapshotFile(cacheDir, url).apply { writeText("""{"words":["历史坏词"]}""") }

        val manager = FilterThesaurusManager(contextWith(url, cacheDir))
        try {
            runBlocking { manager.reload() }

            assertTrue(
                snapshot.readText().contains("历史坏词"),
                "坏响应不得覆盖历史快照（实际内容：${snapshot.readText()}）"
            )
            assertTrue(
                manager.process("这里包含历史坏词").matchedWords.isNotEmpty(),
                "既然快照未被覆盖，降级后词库仍应生效"
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `a slow source times out and degrades to the snapshot`() {
        // 超时是"云端抖动"最常见的形态：必须降级而不是把过滤清空
        val url = serve(body = """{"words":["新词"]}""", delayMs = 1_500)
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-slow").toFile()
        snapshotFile(cacheDir, url).writeText("""{"words":["历史坏词"]}""")

        val context = contextWith(url, cacheDir)
        context.set("filter.cloud.timeout_ms", 300)
        val manager = FilterThesaurusManager(context)

        try {
            runBlocking { manager.reload() }

            assertTrue(
                manager.process("这里包含历史坏词").matchedWords.isNotEmpty(),
                "源超时后必须回落到上一次快照（否则一次抖动就让过滤失效）"
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `an oversized response is refused before being read into memory`() {
        // 声明 64MB 而实际只写几字节：验证的是**预检分支**（contentLengthLong），
        // 因此测试既不需要真的分配 64MB，也不需要等它传完。
        val url = serve(status = 200, body = """{"words":["超大新词"]}""", declaredLength = 64L * 1024 * 1024)
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-big").toFile()
        val snapshot = snapshotFile(cacheDir, url).apply { writeText("""{"words":["历史坏词"]}""") }

        val manager = FilterThesaurusManager(contextWith(url, cacheDir))
        try {
            runBlocking { manager.reload() }

            assertTrue(
                snapshot.readText().contains("历史坏词"),
                "超限响应不得覆盖快照（实际：${snapshot.readText()}）"
            )
            assertTrue(
                manager.process("这里包含历史坏词").matchedWords.isNotEmpty(),
                "超限后必须降级读快照，而不是让过滤失效"
            )
            assertTrue(
                manager.process("这里包含超大新词").matchedWords.isEmpty(),
                "超限响应不得被采用"
            )
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun `an out of order reload does not resurrect the stale thesaurus`() {
        // 并发 reload 的现实来源：管理员连点两次重载 / 定时任务与手动重载撞车。
        // reloadGeneration 的契约是"只有最新一次的结果生效"，慢的那次不得覆盖快的。
        val slowUrl = serve(body = """{"words":["慢词"]}""", delayMs = 900)
        val fastUrl = serve(body = """{"words":["快词"]}""")
        val cacheDir = java.nio.file.Files.createTempDirectory("plumbot-thesaurus-race").toFile()
        val context = contextWith(slowUrl, cacheDir)
        val manager = FilterThesaurusManager(context)

        try {
            runBlocking {
                val slow = launch { manager.reload() }
                delay(150)
                context.set("filter.cloud.urls", listOf(fastUrl))
                manager.reload()
                slow.join()
            }

            assertTrue(
                manager.process("这里包含快词").matchedWords.isNotEmpty(),
                "最新一次 reload 的结果必须生效"
            )
            assertTrue(
                manager.process("这里包含慢词").matchedWords.isEmpty(),
                "较慢的旧 reload 不得在完成后把过期词库写回（乱序覆盖）"
            )
        } finally {
            manager.shutdown()
        }
    }
}
