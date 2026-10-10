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

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * R28 验收：模板**校验和**。
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

        val malformed = TemplateIntegrity.malformedMessage("not-a-hash")
        assertTrue(malformed.contains("64 位十六进制"), "要说明正确格式：$malformed")
    }
}
