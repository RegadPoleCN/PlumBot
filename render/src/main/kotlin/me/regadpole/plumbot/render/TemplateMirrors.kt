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

import java.net.URI

/**
 * 模板候选地址。
 *
 * `template_url` 只有一个地址，而 GitHub releases 在国内常年时好时坏，一次 404 / 超时
 * 就让 Playwright 出图全灭（`PW_TEMPLATE_MISSING`）。于是内置 3 个公共加速站：官方直链
 * 优先、失败才换下一个。加速站只做**前缀代理**（站址 + 官方直链），不假设它不改写内容，
 * 内容仍由 `template_sha256` 把关。
 *
 * 选择顺序：
 * 1. `render.playwright.template_urls` 非空 → **完全覆盖**内置序列（哪怕只写一个地址）；
 * 2. 否则 `template_url`（缺省为 [OFFICIAL_URL]）打头；
 * 3. 起点是 `github.com` 的 https 地址时追加三个前缀候选；自定义 / 自建镜像不加前缀
 *    —— `https://ghproxy.net/https://my.mirror/x.html` 没有意义。
 *
 * 这里只负责**列出**候选；逐跳准入与内容完整性分别由 [SafeHttp]（仅 https）和
 * `template_sha256` 把关，且校验失败**不换镜像重试**。
 */
internal object TemplateMirrors {

    /** 官方直链；`template_url` 缺失时的默认起点。 */
    const val OFFICIAL_URL =
        "https://github.com/RegadPoleCN/PlumBot-Web/releases/latest/download/index.html"

    /** 公共加速站的前缀形式（站址 + 官方直链）。顺序即尝试顺序。 */
    internal val ACCELERATOR_PREFIXES: List<String> = listOf(
        "https://ghproxy.net/",
        "https://gh-proxy.com/",
        "https://ghfast.top/"
    )

    /**
     * 按配置给出候选序列（已去重，保留首次出现顺序）。
     *
     * @param templateUrl `render.playwright.template_url`（单地址兼容项）。
     * @param templateUrls `render.playwright.template_urls`（非空即完全覆盖）。
     */
    fun candidates(templateUrl: String?, templateUrls: List<String?>): List<String> {
        val explicit = templateUrls.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }
        if (explicit.isNotEmpty()) return explicit.distinct()

        val base = templateUrl?.trim()?.takeIf { it.isNotEmpty() } ?: OFFICIAL_URL
        if (!isGitHubHttps(base)) return listOf(base)
        return (listOf(base) + ACCELERATOR_PREFIXES.map { prefix -> prefix + base }).distinct()
    }

    /** 单个候选的准入判定；返回 null 表示可以尝试。 */
    fun rejection(url: String): String? {
        if (!url.startsWith("https://", ignoreCase = true)) return "仅支持 https 直链"
        val uri = runCatching { URI(url) }.getOrNull() ?: return "地址无法解析"
        if (uri.host.isNullOrBlank()) return "缺少主机名"
        return null
    }

    internal fun isGitHubHttps(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) &&
            runCatching { URI(url).host }.getOrNull()?.equals("github.com", ignoreCase = true) == true
}
