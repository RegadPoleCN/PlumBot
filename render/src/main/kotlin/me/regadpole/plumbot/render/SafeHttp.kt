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

/**
 * 受控 HTTP 下载 —— 逐跳校验的重定向跟随。
 *
 * ## 为什么不能直接用 `instanceFollowRedirects = true`
 * 出口白名单与"解析后 IP 不得是内网/回环"这两道校验都作用在**初始 URL** 上；
 * 开启自动跳转后，被允许的主机只要回一个 `302 Location: http://169.254.169.254/...`
 * （或 `http://127.0.0.1:port`），请求就会带着白名单的"通行证"打到内网地址 —— 典型 SSRF。
 *
 * 因此这里**关闭自动跳转**，改为手动跟随：每一跳都重新做 scheme / 白名单 / IP 校验，
 * 任一跳不合法立即终止。
 */
internal object SafeHttp {

    /** 允许的最大跳转次数（含初始请求共 MAX_REDIRECTS + 1 次请求）。 */
    const val MAX_REDIRECTS = 3

    /** URI 层面的准入判定；返回 null 表示通过，否则返回拒绝原因（用于日志）。 */
    fun rejectionReason(uri: URI?, allowedHosts: Set<String>?): String? {
        if (uri == null) return "地址无法解析"
        if (!uri.scheme.equals("https", ignoreCase = true)) return "仅允许 https：$uri"
        val host = uri.host
        if (host.isNullOrBlank()) return "缺少主机名：$uri"
        if (allowedHosts != null && !RenderSecurity.isHostAllowed(host, allowedHosts)) {
            return "主机不在出口白名单内：$host"
        }
        if (RenderSecurity.resolvesToBlockedAddress(host)) return "主机解析到内网/回环地址：$host"
        return null
    }

    /** 解析 `Location`（支持相对地址），失败返回 null。 */
    fun resolveRedirect(from: URI, location: String?): URI? {
        if (location.isNullOrBlank()) return null
        return runCatching { from.resolve(location.trim()) }.getOrNull()
    }

    /**
     * 内容嗅探：返回真实 MIME 类型，未识别返回 null。
     *
     * 用途有二：① 判断"是不是图片"（拿 `200` 返回 HTML 错误页的图床不少见）；
     * ② **生成 data URL 时给出正确的前缀** —— 此前两处 `toDataUrl` 硬编码 `image/png`，
     * 而嗅探是允许 JPEG/GIF/WEBP 通过的，于是声明的 MIME 与实际内容不符（数据契约错误）。
     */
    fun mimeOf(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        fun at(i: Int) = bytes[i].toInt() and 0xFF
        return when {
            // PNG: 89 50 4E 47
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
            // JPEG: FF D8 FF
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
            // GIF: "GIF8"
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38 -> "image/gif"
            // WEBP: "RIFF" .... "WEBP"
            bytes.size >= 12 && at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
                at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50 -> "image/webp"

            else -> null
        }
    }

    /** 是否是可识别的图片内容（见 [mimeOf]）。 */
    fun looksLikeImage(bytes: ByteArray): Boolean = mimeOf(bytes) != null

    /**
     * 打开一个**已经过逐跳校验**的连接。
     *
     * @return 返回码为 2xx 的连接；被拒绝、超跳数或非 2xx 时返回 null（连接已关闭）
     */
    fun openValidated(
        url: String,
        allowedHosts: Set<String>,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accepted: String? = null,
        log: (String) -> Unit,
        /**
         * 连接工厂 —— **仅供测试注入**（默认走真实网络）。
         *
         * 为什么需要它：本模块只允许 https 且拒绝内网/回环地址，因此"起一个本地 HTTP 服务器"
         * 在物理上不可能成为合法起点（目标在建立连接**之前**就被拒了）。要验证真实的逐跳链路
         * （302 链、超跳数、降级），只能替换连接建立这一步。
         */
        connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    ): HttpURLConnection? = open(url, allowedHosts, userAgent, connectTimeoutMs, readTimeoutMs, accepted, log, connectionFactory)

    /**
     * 与 [openValidated] 相同，但**不做出口白名单**限制，只保证 https + 非内网/回环。
     *
     * 用于"来源由管理员配置的直链"（例如模板下载）：这类地址没有固定域名清单，
     * 但仍不应被 302 引到内网地址。
     */
    fun openValidatedWithoutAllowlist(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accepted: String? = null,
        log: (String) -> Unit,
        connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    ): HttpURLConnection? = open(url, null, userAgent, connectTimeoutMs, readTimeoutMs, accepted, log, connectionFactory)

    private fun open(
        url: String,
        allowedHosts: Set<String>?,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accepted: String?,
        log: (String) -> Unit,
        connectionFactory: (URI) -> HttpURLConnection,
    ): HttpURLConnection? {
        var current: URI? = runCatching { URI(url) }.getOrNull()

        for (hop in 0..MAX_REDIRECTS) {
            val rejection = rejectionReason(current, allowedHosts)
            val uri = current
            if (rejection != null || uri == null) {
                log("[PlumBot-Render] 下载被拒绝（$rejection）：$url")
                return null
            }

            var conn: HttpURLConnection? = null
            try {
                conn = connectionFactory(uri)
                conn.requestMethod = "GET"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                // 关键：不自动跳转，跳转由本方法逐跳校验
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", userAgent)
                accepted?.let { conn.setRequestProperty("Accept", it) }

                val code = conn.responseCode
                if (code in 200..299) return conn

                if (code in 300..399) {
                    val next = resolveRedirect(uri, conn.getHeaderField("Location"))
                    conn.disconnect()
                    if (next == null) {
                        log("[PlumBot-Render] 跳转缺少 Location，已终止：$uri")
                        return null
                    }
                    log("[PlumBot-Render] 跟随跳转（$code）：$uri -> $next")
                    current = next
                    continue
                }

                log("[PlumBot-Render] 响应码 $code，已终止：$uri")
                conn.disconnect()
                return null
            } catch (e: IOException) {
                conn?.disconnect()
                log("[PlumBot-Render] 连接失败（$uri）：${e.message}")
                return null
            }
        }

        log("[PlumBot-Render] 跳转次数超过 $MAX_REDIRECTS 次，已终止：$url")
        return null
    }
}
