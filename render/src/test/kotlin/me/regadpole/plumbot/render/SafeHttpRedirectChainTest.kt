// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.render.support.SafeHttp

/**
 * **逐跳链路的真实驱动**。
 *
 * ## 准入口径
 * 起点只查 `https` + 白名单，跳转目标只查 `https`：CDN / 镜像站 302 到自有域名是常态，
 * 逐跳查白名单会把正常下载误杀；IP 审查已整段删除（fake-ip 环境下校验时的解析结果与
 * 真正建连时可能不同，据此拦截只会误伤）。
 *
 * ## 为什么不是"起一个本地 HTTP 服务器"
 * 注入连接工厂比它更可控：把**连接建立**这一步换成替身，其余逐跳判定逻辑全部走真实代码，
 * 也因此能验证"302 指向内网时请求确实会发出去"这类残余面。
 */
class SafeHttpRedirectChainTest {

    private val allowlist = setOf("example.com")

    /** 记录被建立连接的 URI，并可切换返回码/Location/异常。 */
    private class FakeTransport : (URI) -> HttpURLConnection {
        val attempts = mutableListOf<String>()
        var code: Int = 200
        var location: String? = null
        var failWith: IOException? = null

        override fun invoke(uri: URI): HttpURLConnection {
            attempts += uri.toString()
            failWith?.let { throw it }
            return FakeConnection(uri, code, location)
        }
    }

    private class FakeConnection(
        uri: URI,
        private val code: Int,
        private val location: String?,
    ) : HttpURLConnection(URL(uri.toString())) {
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = code
        override fun getHeaderField(name: String?): String? =
            if (name.equals("Location", ignoreCase = true)) location else null
    }

    /** 捕获 [SafeHttp] 的**分级**日志（逐跳明细必须落在 DEBUG，拒绝原因才占 WARN）。 */
    private fun open(
        transport: FakeTransport,
        url: String = "https://example.com/a.png",
        logs: MutableList<Pair<LogLevel, String>>? = null,
    ) =
        SafeHttp.openValidated(
            url = url,
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = { level, message -> logs?.add(level to message) },
            connectionFactory = transport,
        )

    @Test
    fun `a 302 chain stops after MAX_REDIRECTS hops`() {
        val transport = FakeTransport().apply {
            code = 302
            location = "https://example.com/again.png" // 自跳转
        }

        val logs = mutableListOf<Pair<LogLevel, String>>()
        assertNull(open(transport, logs = logs), "自跳转必须被跳数上限终止")
        assertEquals(
            SafeHttp.MAX_REDIRECTS + 1,
            transport.attempts.size,
            "上限内每一跳都要真的发起连接（共 MAX_REDIRECTS + 1 次）",
        )
        assertTrue(
            logs.any { it.first == LogLevel.WARN && it.second.contains("跳转次数超过") },
            "超限必须留一条 WARN：$logs",
        )
    }

    @Test
    fun `a 302 leaving the allowlist is followed`() {
        // 白名单只约束起点，CDN / 镜像站的自有域名必须能跟上
        val attempts = mutableListOf<String>()
        val transport = object : (URI) -> HttpURLConnection {
            override fun invoke(uri: URI): HttpURLConnection {
                attempts += uri.toString()
                val first = attempts.size == 1
                return FakeConnection(uri, if (first) 302 else 200, if (first) "https://cdn.example.org/x.png" else null)
            }
        }

        val logs = mutableListOf<Pair<LogLevel, String>>()
        val conn = SafeHttp.openValidated(
            url = "https://example.com/a.png",
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = { level, message -> logs += level to message },
            connectionFactory = transport,
        )

        assertNotNull(conn, "同为 https 的跳转目标应被跟随")
        assertEquals(listOf("https://example.com/a.png", "https://cdn.example.org/x.png"), attempts)
        assertTrue(logs.none { it.first == LogLevel.WARN }, "正常跳转不得产生 WARN：$logs")
    }

    @Test
    fun `a 302 that downgrades https to http is refused`() {
        val transport = FakeTransport().apply {
            code = 302
            location = "http://example.com/x.png"
        }

        assertNull(open(transport))
        assertEquals(1, transport.attempts.size)
    }

    @Test
    fun `a 302 to an https intranet address is attempted`() {
        // 已知残余面：跳转目标只复核 https，不再做 IP 审查，因此这一跳会真的发出请求。
        // 内容仍受体积上限 / sha256 / 图片签名嗅探约束。
        val attempts = mutableListOf<String>()
        val transport = object : (URI) -> HttpURLConnection {
            override fun invoke(uri: URI): HttpURLConnection {
                attempts += uri.toString()
                val first = attempts.size == 1
                return FakeConnection(uri, if (first) 302 else 200, if (first) "https://127.0.0.1/secret.png" else null)
            }
        }

        val conn = SafeHttp.openValidated(
            url = "https://example.com/a.png",
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = { _, _ -> },
            connectionFactory = transport,
        )

        assertNotNull(conn, "https 跳转仍被跟随（残余面）")
        assertEquals("https://127.0.0.1/secret.png", attempts.last(), "这一跳确实被连接了")
    }

    @Test
    fun `an allowed redirect is followed and the final 2xx is returned`() {
        val seen = AtomicInteger()
        val transport = FakeTransport().apply {
            code = 200
        }
        // 第一次 302 到另一个白名单内地址，第二次 200
        val redirecting = object : (URI) -> HttpURLConnection {
            override fun invoke(uri: URI): HttpURLConnection {
                val n = seen.incrementAndGet()
                transport.attempts += uri.toString()
                return FakeConnection(uri, if (n == 1) 302 else 200, if (n == 1) "https://cdn.example.com/ok.png" else null)
            }
        }

        val logs = mutableListOf<Pair<LogLevel, String>>()
        val conn = SafeHttp.openValidated(
            url = "https://example.com/a.png",
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = { level, message -> logs += level to message },
            connectionFactory = redirecting,
        )

        assertNotNull(conn, "白名单内的跳转应当被跟随并返回 2xx 连接")
        assertEquals(2, seen.get(), "应跟随一次跳转后拿到 200")
        // 正常跟随不得占用 WARN —— 302 是常态，刷屏就来自这里。
        assertTrue(logs.none { it.first == LogLevel.WARN }, "成功跟随的跳转不应产生 WARN：$logs")
        val follow = logs.first { it.second.contains("跟随跳转") }
        assertEquals(LogLevel.DEBUG, follow.first, "逐跳明细必须是 DEBUG：$logs")
        assertTrue(follow.second.contains("第 1 跳"), "逐跳明细必须带跳数，便于复盘链路：$logs")
    }

    @Test
    fun `a non 2xx non redirect response terminates with null`() {
        val transport = FakeTransport().apply { code = 404 }
        assertNull(open(transport))
    }

    @Test
    fun `a connect failure fails closed`() {
        val transport = FakeTransport().apply { failWith = IOException("boom") }
        assertNull(open(transport), "连接异常必须按失败处理（fail-closed），不得返回半个连接")
    }
}
