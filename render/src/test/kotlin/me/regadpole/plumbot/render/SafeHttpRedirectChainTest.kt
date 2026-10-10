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

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A18 验收（**逐跳链路的真实驱动**）。
 *
 * ## 为什么不是"起一个本地 HTTP 服务器"
 * 这是原计划里的写法，但**物理上不可行**：本模块只允许 `https`，并且拒绝一切内网/回环地址，
 * 因此 `http://127.0.0.1:port` 这种本地服务器永远不可能成为合法起点 ——
 * 它在建立连接**之前**就被 [SafeHttp.rejectionReason] 拒掉了。
 *
 * 替代方案同样能覆盖"302 链 / 超跳数 / 降级"这些验收点，而且更快更稳：
 * 注入连接工厂，把**连接建立**这一步换成可控的替身，其余逐跳判定逻辑全部走真实代码。
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

    private fun open(transport: FakeTransport, url: String = "https://example.com/a.png") =
        SafeHttp.openValidated(
            url = url,
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = {},
            connectionFactory = transport,
        )

    @Test
    fun `a 302 pointing at an intranet address is refused before connecting`() {
        val transport = FakeTransport().apply {
            code = 302
            location = "https://127.0.0.1/secret.png"
        }

        assertNull(open(transport), "跳转目标解析到回环地址，必须拒绝")
        assertEquals(1, transport.attempts.size, "被拒的目标**不得再发起连接**（否则 SSRF 已经打出去了）")
    }

    @Test
    fun `a 302 leaving the allowlist is refused`() {
        val transport = FakeTransport().apply {
            code = 302
            location = "https://evil.example.org/x.png"
        }

        assertNull(open(transport))
        assertEquals(1, transport.attempts.size, "白名单外目标不得被连接")
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
    fun `a redirect loop is bounded instead of spinning forever`() {
        val transport = FakeTransport().apply {
            code = 302
            location = "https://example.com/a.png" // 指向自己
        }

        assertNull(open(transport), "自跳转必须被超跳数上限终止")
        assertTrue(transport.attempts.size in 2..10, "跳转次数必须有上限，实测 ${transport.attempts.size} 次")
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

        val conn = SafeHttp.openValidated(
            url = "https://example.com/a.png",
            allowedHosts = allowlist,
            userAgent = "PlumBot-test",
            connectTimeoutMs = 1_000,
            readTimeoutMs = 1_000,
            log = {},
            connectionFactory = redirecting,
        )

        assertNotNull(conn, "白名单内的跳转应当被跟随并返回 2xx 连接")
        assertEquals(2, seen.get(), "应跟随一次跳转后拿到 200")
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
