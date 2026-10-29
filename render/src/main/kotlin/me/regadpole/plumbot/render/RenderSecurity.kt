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
import java.net.URI
import java.nio.file.Paths

/**
 * 渲染期的路径与网络守卫。
 *
 * 取代旧实现的三个薄弱点：
 * 1. 路径仅把 `\` 换成 `/`，含空格/中文时 `file://` URL 非法（改用 [File.toBrowserSafeUrl] 做百分号编码）；
 * 2. `file://` 放行**整个数据目录**（内含 `config.yml` 的数据库口令等），收紧为**仅主题根目录**；
 * 3. 网络出口只做 **仅 https + 起点主机白名单**，不再解析目标域名、不再做 IP/CIDR 审查：
 *    宿主机常见 DNS 代理 / fake-ip，校验时的解析结果与真正建连时可能不同（DNS 重绑定），
 *    据此拦截只会误伤正常渲染；白名单只约束下载起点，跳转链上的 CDN / 镜像站域名不逐一校验。
 */
internal object RenderSecurity {

    /** 未配置出口白名单时使用的内置清单。 */
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
        val configured = config.getStringList("render", "avatar", "domains")
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

    /** 从 URL 中取出主机名；解析失败返回 null。 */
    internal fun hostOf(url: String): String? =
        runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
