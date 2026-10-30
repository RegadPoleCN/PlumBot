// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.platform.LogLevel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * 受控 HTTP 下载 —— 手动逐跳跟随重定向。
 *
 * ## 为什么不用 `instanceFollowRedirects = true`
 * 这里要的是**逐跳可见**：每一跳的响应码、Location、拒绝原因都要落进日志，
 * 而 JDK 的自动跳转把整条链吞进去后只留一个终态，现场问题无法复盘。
 * 代价是上限要自己掌握：`MAX_REDIRECTS = 20`，超过即终止。
 *
 * ## 出口约束
 * - **起点**：仅 https + 主机在白名单内（模板下载没有固定域名清单，只查 https）；
 * - **跳转**：每一跳仍必须 https，但**不再校验白名单** —— CDN / 镜像站把资源 302 到自己的
 *   域名是常态，逐跳校验会把正常下载误杀；
 * - 不再解析目标域名、不再做 IP / CIDR 审查：宿主机常见 DNS 代理 / fake-ip
 *   （本机实测 example.com 解析到 198.18.0.74、q1.qlogo.cn 解析到 198.18.0.75），校验时的
 *   解析结果与真正建连时可能不同（DNS 重绑定），据此拦截只会误伤。**已知残余面**：https
 *   直连内网地址的请求仍会发出，内容由体积上限 / sha256 / 图片签名嗅探兜底。
 *
 * ## 日志分级
 * - WARN：被拒绝（非 https / 起点在名单外 / 缺 Location / 超跳数）—— 值得抬头；
 * - DEBUG：逐跳跟随（带跳数）、非 2xx / 3xx 终态、连接失败 —— 只在 `debug.enable: true` 时可见；
 *
 * 下载的"开始 / 完成 / 全候选失败"由调用方（[ThemeManager] / [AvatarCache]）各打一行
 * INFO / ERROR，不在这里逐条重复。
 */
internal object SafeHttp {

    /** 允许的最大跳转次数（含初始请求共 MAX_REDIRECTS + 1 次请求）。 */
    const val MAX_REDIRECTS = 20

    /** https-only 判定，起点与跳转共用。 */
    private fun schemeAdmission(uri: URI?): String? {
        if (uri == null) return "地址无法解析"
        if (!uri.scheme.equals("https", ignoreCase = true)) return "仅允许 https：$uri"
        if (uri.host.isNullOrBlank()) return "缺少主机名：$uri"
        return null
    }

    /**
     * **起点**准入判定：仅 https + 主机白名单（`allowedHosts == null` 时只查 https）。
     *
     * 白名单只约束起点：CDN / 镜像站经常把资源 302 到自己的域名，逐跳校验会把正常
     * 下载误杀。返回 null 表示通过，否则返回拒绝原因（用于日志）。
     */
    fun rejectionReason(uri: URI?, allowedHosts: Set<String>?): String? {
        schemeAdmission(uri)?.let { return it }
        val host = uri!!.host
        if (allowedHosts != null && !RenderSecurity.isHostAllowed(host, allowedHosts)) {
            return "主机不在出口白名单内：$host"
        }
        return null
    }

    /** **跳转目标**准入判定：只要求仍是 https；起点白名单不适用于跳转目标。 */
    fun redirectRejectionReason(uri: URI?): String? = schemeAdmission(uri)

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
     * 打开一个**起点已通过准入判定**的连接。
     *
     * 白名单只在起点校验；跳转目标只要求 https。
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
        log: (LogLevel, String) -> Unit,
        /**
         * 连接工厂 —— **仅供测试注入**（默认走真实网络）。
         *
         * 为什么需要它：本模块只允许 https，而本地测试服务器通常跑在 `http://127.0.0.1:port`，
         * 在建立连接**之前**就会被 [rejectionReason] 拒掉。要验证真实的逐跳链路
         * （302 链、超跳数、降级），只能替换连接建立这一步。
         */
        connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    ): HttpURLConnection? = open(url, allowedHosts, userAgent, connectTimeoutMs, readTimeoutMs, accepted, log, connectionFactory)

    /**
     * 与 [openValidated] 相同，但**起点**不做白名单限制，只保证 https。
     *
     * 用于"来源由管理员配置的直链"（例如模板下载）：这类地址没有固定域名清单。
     */
    fun openValidatedWithoutAllowlist(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accepted: String? = null,
        log: (LogLevel, String) -> Unit,
        connectionFactory: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    ): HttpURLConnection? = open(url, null, userAgent, connectTimeoutMs, readTimeoutMs, accepted, log, connectionFactory)

    private fun open(
        url: String,
        allowedHosts: Set<String>?,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        accepted: String?,
        log: (LogLevel, String) -> Unit,
        connectionFactory: (URI) -> HttpURLConnection,
    ): HttpURLConnection? {
        var current: URI? = runCatching { URI(url) }.getOrNull()

        for (hop in 0..MAX_REDIRECTS) {
            val uri = current
            // 起点（hop 0）过白名单；后续跳转只要求 https
            val rejection = if (hop == 0) rejectionReason(uri, allowedHosts) else redirectRejectionReason(uri)
            if (rejection != null || uri == null) {
                log(LogLevel.WARN, "[PlumBot-Render] 下载被拒绝（$rejection）：$uri")
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
                        log(LogLevel.WARN, "[PlumBot-Render] 跳转缺少 Location，已终止：$uri")
                        return null
                    }
                    log(LogLevel.DEBUG, "[PlumBot-Render] 跟随跳转（$code，第 ${hop + 1} 跳）：$uri -> $next")
                    current = next
                    continue
                }

                // 非 2xx/3xx 是**这一跳**的终态；上层会把多个候选的失败汇总成一条 ERROR。
                log(LogLevel.DEBUG, "[PlumBot-Render] 响应码 $code，已终止：$uri")
                conn.disconnect()
                return null
            } catch (e: IOException) {
                conn?.disconnect()
                log(LogLevel.DEBUG, "[PlumBot-Render] 连接失败（$uri）：${e.message}")
                return null
            }
        }

        log(LogLevel.WARN, "[PlumBot-Render] 跳转次数超过 $MAX_REDIRECTS 次，已终止：$url")
        return null
    }
}
