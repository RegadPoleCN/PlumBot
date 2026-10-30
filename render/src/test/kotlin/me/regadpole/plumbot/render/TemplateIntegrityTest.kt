// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 模板**校验和**。
 *
 * 关键不是"能算哈希"，而是三种配置状态的行为差异 —— 尤其是**格式非法必须失败关闭**：
 * 静默跳过会把安全开关变成安慰剂（服主以为在校验，其实没有）。
 */
class TemplateIntegrityTest {

    /** 已知向量：空串的 SHA-256。 */
    private val emptySha = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun `blank configuration means no verification`() {
        assertIs<TemplateIntegrity.Expected.NotConfigured>(TemplateIntegrity.parse(null))
        assertIs<TemplateIntegrity.Expected.NotConfigured>(TemplateIntegrity.parse(""))
        assertIs<TemplateIntegrity.Expected.NotConfigured>(TemplateIntegrity.parse("   "))
    }

    @Test
    fun `accepts hex with common decorations`() {
        assertIs<TemplateIntegrity.Expected.Valid>(TemplateIntegrity.parse(emptySha))
        assertIs<TemplateIntegrity.Expected.Valid>(TemplateIntegrity.parse(emptySha.uppercase()))
        assertIs<TemplateIntegrity.Expected.Valid>(TemplateIntegrity.parse("sha256:$emptySha"))
        assertIs<TemplateIntegrity.Expected.Valid>(TemplateIntegrity.parse("  sha256:$emptySha  "))
    }

    @Test
    fun `a malformed value is rejected instead of silently ignored`() {
        listOf("zzz", "abc123", emptySha.dropLast(1) + "g", "3f2a").forEach { raw ->
            val parsed = TemplateIntegrity.parse(raw)
            assertIs<TemplateIntegrity.Expected.Malformed>(parsed, "非法值必须被识别为 Malformed：$raw")
        }
    }

    @Test
    fun `hashes a file and matches case insensitively`() {
        val file = Files.createTempFile("plumbot-template", ".html").toFile()
        file.writeText("") // 已知向量：空文件
        try {
            assertEquals(emptySha, TemplateIntegrity.sha256(file))
            assertTrue(TemplateIntegrity.matches(emptySha, emptySha.uppercase()))
            assertFalse(TemplateIntegrity.matches(emptySha, "0".repeat(64)))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `null expected means the file is accepted`() {
        assertTrue(TemplateIntegrity.matches(emptySha, null), "未配置校验和时应放行（默认行为）")
    }

    @Test
    fun `messages tell the operator what to do`() {
        val mismatch = TemplateIntegrity.mismatchMessage("expect", "actual")
        assertTrue(mismatch.contains("expect") && mismatch.contains("actual"), "要给出期望值与实际值以便对照：$mismatch")
        assertTrue(mismatch.contains("template_sha256"), "要指出配置键：$mismatch")

        val withSource = TemplateIntegrity.mismatchMessage("expect", "actual", "https://ghproxy.net/x.html")
        assertTrue(withSource.contains("https://ghproxy.net/x.html"), "要指出是哪个候选给出的文件：$withSource")
        assertTrue(withSource.contains("可能原因"), "要说清可能原因，避免管理员第一反应是清空 sha256：$withSource")
        assertTrue(withSource.contains("不会换镜像重试"), "要说明不会换镜像重试：$withSource")

        val malformed = TemplateIntegrity.malformedMessage("not-a-hash")
        assertTrue(malformed.contains("64 位十六进制"), "要说明正确格式：$malformed")
    }
}
