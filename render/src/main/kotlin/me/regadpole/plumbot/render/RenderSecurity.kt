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

import java.io.File
import java.net.InetAddress
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * 渲染期的路径与网络守卫。
 *
 * 取代旧实现的三个薄弱点：
 * 1. 路径仅把 `\` 换成 `/`，含空格/中文时 `file://` URL 非法（改用 [File.toBrowserSafeUrl] 做百分号编码）；
 * 2. `file://` 放行**整个数据目录**（内含 `config.yml` 的数据库口令等），收紧为**仅主题根目录**；
 * 3. SSRF 用字符串 `contains` 黑名单（`http://2130706433/`、八进制、IPv6-mapped、指向内网的域名均可绕过），
 *    改为**解析后的 IP 判定 + 主机白名单**双重控制。
 */
internal object RenderSecurity {

    /**
     * 私有 / 环回 / 链路本地 / 保留 IPv4 网段。
     *
     * 用「网络号 + 掩码」表示，避免只比较整字节前缀时漏掉 `/12`、`/10`、`/15` 这类非整字节掩码
     * （例如 `172.16.0.0/12` 实际覆盖 172.16–172.31）。
     */
    private class Cidr(val network: IntArray, val mask: IntArray)

    private val BLOCKED_V4: List<Cidr> = listOf(
        Cidr(intArrayOf(0), intArrayOf(0xFF)), // 0.0.0.0/8
        Cidr(intArrayOf(10), intArrayOf(0xFF)), // 10.0.0.0/8
        Cidr(intArrayOf(100, 64), intArrayOf(0xFF, 0xC0)), // 100.64.0.0/10 (CGNAT)
        Cidr(intArrayOf(127), intArrayOf(0xFF)), // 127.0.0.0/8
        Cidr(intArrayOf(169, 254), intArrayOf(0xFF, 0xFF)), // 169.254.0.0/16（云元数据）
        Cidr(intArrayOf(172, 16), intArrayOf(0xFF, 0xF0)), // 172.16.0.0/12
        Cidr(intArrayOf(192, 0, 0), intArrayOf(0xFF, 0xFF, 0xFF)), // 192.0.0.0/24
        Cidr(intArrayOf(192, 168), intArrayOf(0xFF, 0xFF)), // 192.168.0.0/16
        Cidr(intArrayOf(198, 18), intArrayOf(0xFF, 0xFE)), // 198.18.0.0/15
        Cidr(intArrayOf(224), intArrayOf(0xF0)), // 224.0.0.0/4 组播
        Cidr(intArrayOf(240), intArrayOf(0xF0)) // 240.0.0.0/4 保留
    )

    private val hostResolutionCache = ConcurrentHashMap<String, Boolean>()

    /** 基线 §3.4 的内置出口白名单。 */
    internal val DEFAULT_AVATAR_HOSTS: Set<String> = setOf(
        "textures.minecraft.net",
        "sessionserver.mojang.com",
        "crafatar.com",
        "mc-heads.net",
        "minotar.net",
        "q1.qlogo.cn"
    )

    /** 读取配置中的出口白名单；未配置时回落到内置列表。 */
    internal fun allowedHostsFrom(config: me.regadpole.plumbot.api.platform.PlatformConfig): Set<String> {
        val configured = config.getStringList("feature", "img", "avatar", "domains")
            ?.filterNotNull()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        return if (configured.isEmpty()) DEFAULT_AVATAR_HOSTS else configured.toSet()
    }

    /** 生成浏览器可用的 `file:///` URL（百分号编码，兼容空格 / 中文 / UNC）。 */
    internal fun File.toBrowserSafeUrl(): String {
        val encoded = this.absoluteFile.normalize().toURI().toASCIIString()
        return when {
            // 已含 authority（UNC）或已是标准 file:/// 形式 → 原样返回
            encoded.startsWith("file://") -> encoded
            encoded.startsWith("file:/") -> "file:///" + encoded.removePrefix("file:/")
            else -> encoded
        }
    }

    /**
     * 判断 `file:` URL 是否落在允许的根目录之内。
     * 使用真实文件路径而非字符串前缀比较，避免大小写/编码差异带来的绕过。
     */
    internal fun isFileUrlWithinRoot(url: String, rootDir: File): Boolean {
        if (!url.startsWith("file:", ignoreCase = true)) return false
        val path = runCatching { Paths.get(URI(url)) }.getOrNull() ?: return false
        val root = rootDir.absoluteFile.normalize().toPath()
        return path.toAbsolutePath().normalize().startsWith(root)
    }

    /**
     * 主机是否命中白名单（支持精确与子域匹配）。
     */
    internal fun isHostAllowed(host: String, allowedHosts: Set<String>): Boolean {
        if (host.isBlank()) return false
        val lower = host.lowercase().trimEnd('.')
        return allowedHosts.any { allowed ->
            val a = allowed.lowercase().trimStart('.').trimEnd('.')
            a.isNotEmpty() && (lower == a || lower.endsWith(".$a"))
        }
    }

    /**
     * 主机是否解析到内网/保留地址。
     *
     * 结果带缓存：每个主机只做一次 DNS 解析，避免逐请求解析拖慢路由处理器。
     * 解析失败时**保守放行**（交由主机白名单兜底），避免因 DNS 抖动误伤正常渲染。
     */
    internal fun resolvesToBlockedAddress(host: String): Boolean {
        val cached = hostResolutionCache[host]
        if (cached != null) return cached
        val blocked = runCatching {
            InetAddress.getAllByName(host).any { isBlockedAddress(it) }
        }.getOrDefault(false)
        hostResolutionCache[host] = blocked
        return blocked
    }

    private fun isBlockedAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isAnyLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        return when (bytes.size) {
            4 -> BLOCKED_V4.any { matchesV4(bytes, it) }
            16 -> {
                // IPv6：唯一本地地址 fc00::/7、链路本地 fe80::/10；IPv4-mapped 走 v4 逻辑
                val first = bytes[0].toInt() and 0xFF
                val second = bytes[1].toInt() and 0xFF
                when {
                    (first and 0xFE) == 0xFC -> true // fc00::/7
                    first == 0xFE && (second and 0xC0) == 0x80 -> true // fe80::/10
                    isIpv4Mapped(bytes) -> isBlockedAddress(InetAddress.getByAddress(bytes.copyOfRange(12, 16)))
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun matchesV4(bytes: ByteArray, cidr: Cidr): Boolean {
        if (bytes.size < cidr.network.size) return false
        for (i in cidr.network.indices) {
            val octet = bytes[i].toInt() and 0xFF
            if ((octet and cidr.mask[i]) != cidr.network[i]) return false
        }
        return true
    }

    private fun isIpv4Mapped(bytes: ByteArray): Boolean {
        for (i in 0 until 10) if (bytes[i] != 0.toByte()) return false
        return bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
    }

    /** 从 URL 中取出主机名；解析失败返回 null。 */
    internal fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
