// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.theme

import java.io.File
import java.security.MessageDigest

/**
 * 模板完整性校验：把"这个模板确实是发布时那一份"变成可验证事实。
 *
 * 模板是**被渲染的 HTML**：它决定出图内容，也能通过内联脚本影响浏览器进程。只校验
 * "https 直链 + 体积上限"时，直链被替换（或被 CDN 投毒）无法察觉。
 *
 * 三种配置状态（**格式错误必须失败关闭**）：
 * | 配置 | 行为 |
 * |---|---|
 * | 空/未设置 | 不校验（默认；向后兼容）|
 * | 合法 64 位十六进制 | 校验；不匹配即拒绝替换模板 |
 * | 写了但格式非法 | **拒绝**并报错 —— 静默跳过等于把安全开关变成安慰剂 |
 */
internal object TemplateIntegrity {

    /** 配置解析结果。 */
    sealed interface Expected {
        /** 未配置：跳过校验。 */
        data object NotConfigured : Expected

        /** 已配置且格式合法（小写十六进制）。 */
        data class Valid(val hex: String) : Expected

        /** 已配置但格式非法 —— 调用方必须**拒绝**而不是跳过。 */
        data class Malformed(val raw: String) : Expected
    }

    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    /**
     * 解析配置值。容忍 `sha256:` 前缀与大小写和空白（复制粘贴自发布说明的常见形态）。
     */
    fun parse(raw: String?): Expected {
        val cleaned = raw?.trim()?.removePrefix("sha256:")?.trim() ?: return Expected.NotConfigured
        if (cleaned.isEmpty()) return Expected.NotConfigured
        val lower = cleaned.lowercase()
        return if (HEX_64.matches(lower)) Expected.Valid(lower) else Expected.Malformed(raw.trim())
    }

    /** 计算文件的 SHA-256（小写十六进制）。 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 校验结果判定：`expected` 为 null 表示"未配置"（放行）。
     */
    fun matches(actualHex: String, expectedHex: String?): Boolean =
        expectedHex == null || actualHex.equals(expectedHex, ignoreCase = true)

    /**
     * 给管理员看的失败文案（含期望值、实际值、来源地址，便于对照发布说明）。
     *
     * 校验和不匹配**直接失败、不换镜像重试**：若"换一个候选再试"，校验和就形同虚设
     * （改一个镜像就能绕过）。文案把可能原因列全，避免管理员第一反应是"是不是镜像
     * 挂了"，从而误把 sha256 清空。
     */
    fun mismatchMessage(expected: String, actual: String, source: String? = null): String =
        "[PlumBot-Render] 模板校验和不匹配，已拒绝使用该文件：期望 $expected，实际 $actual" +
            (source?.takeIf { it.isNotBlank() }?.let { "（来源：$it）" } ?: "") + "。" +
            "可能原因：镜像/加速站改写了内容、模板已更新（发布说明里的 sha256 随之变化），或配置已过期。" +
            "已保留旧模板且不会换镜像重试；请核对 template_url / template_urls 与 template_sha256" +
            "（或清空 template_sha256 以显式关闭校验）。"

    /** 配置格式非法时的文案。 */
    fun malformedMessage(raw: String): String =
        "[PlumBot-Render] template_sha256 配置格式非法，已拒绝下载模板：$raw" +
            "（应为 64 位十六进制，可带 sha256: 前缀）"
}
