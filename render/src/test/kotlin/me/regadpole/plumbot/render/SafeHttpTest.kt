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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 逐跳校验：白名单与"解析到内网"判定必须对**每一跳**生效。
 *
 * 背景：旧实现开 `instanceFollowRedirects = true`，被放行的主机只要回一个
 * `302 Location: http://127.0.0.1:port/` 就能带着白名单的通行证打到内网（SSRF）。
 */
class SafeHttpTest {

    private val allowed = setOf("q1.qlogo.cn", "textures.minecraft.net")

    @Test
    fun `https 且在白名单内的主机通过`() {
        assertNull(SafeHttp.rejectionReason(URI("https://q1.qlogo.cn/g?b=qq&nk=1"), allowed))
    }

    @Test
    fun `非 https 被拒绝`() {
        assertNotNull(SafeHttp.rejectionReason(URI("http://q1.qlogo.cn/g?b=qq"), allowed))
    }

    @Test
    fun `白名单外主机被拒绝`() {
        val reason = SafeHttp.rejectionReason(URI("https://evil.example/skin.png"), allowed)
        assertTrue(reason!!.contains("白名单"), "拒绝原因应指明白名单：$reason")
    }

    @Test
    fun `解析到回环或内网的主机被拒绝`() {
        // 直连 IP 与 localhost 都必须被判为不可达外部地址
        assertNotNull(SafeHttp.rejectionReason(URI("https://127.0.0.1:8080/x.png"), setOf("127.0.0.1")))
        assertNotNull(SafeHttp.rejectionReason(URI("https://localhost/x.png"), setOf("localhost")))
    }

    @Test
    fun `跳转地址支持相对与绝对形式`() {
        val base = URI("https://q1.qlogo.cn/a/b.png")

        assertEquals(
            URI("https://q1.qlogo.cn/a/c.png"),
            SafeHttp.resolveRedirect(base, "c.png")
        )
        assertEquals(
            URI("https://q1.qlogo.cn/x.png"),
            SafeHttp.resolveRedirect(base, "/x.png")
        )
        assertEquals(
            URI("https://textures.minecraft.net/skin.png"),
            SafeHttp.resolveRedirect(base, "https://textures.minecraft.net/skin.png")
        )
        assertNull(SafeHttp.resolveRedirect(base, null))
        assertNull(SafeHttp.resolveRedirect(base, "   "))
    }

    @Test
    fun `跳转目标仍要过同一套校验`() {
        // 白名单主机跳转到内网：这一跳必须被拒绝（这正是旧实现漏掉的一步）
        val next = SafeHttp.resolveRedirect(URI("https://q1.qlogo.cn/a.png"), "http://127.0.0.1:8080/secret")
        assertNotNull(next)
        assertNotNull(SafeHttp.rejectionReason(next, allowed), "跳转后的 http 内网地址必须被拒绝")

        val nextHttpsInternal = SafeHttp.resolveRedirect(URI("https://q1.qlogo.cn/a.png"), "https://localhost/x")
        assertNotNull(SafeHttp.rejectionReason(nextHttpsInternal, allowed), "跳转后的回环地址必须被拒绝")
    }

    @Test
    fun `内容嗅探只放行真实图片签名`() {
        assertTrue(SafeHttp.looksLikeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)))
        assertTrue(SafeHttp.looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0)))
        assertTrue(SafeHttp.looksLikeImage("GIF89a".toByteArray()))

        assertFalse(SafeHttp.looksLikeImage("<html><body>404</body></html>".toByteArray()))
        assertFalse(SafeHttp.looksLikeImage(ByteArray(0)))
        assertFalse(SafeHttp.looksLikeImage(byteArrayOf(0x89.toByte(), 0x50)))
    }

    @Test
    fun `内容嗅探给出正确的 MIME 供 data URL 使用`() {
        assertEquals("image/png", SafeHttp.mimeOf(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)))
        assertEquals("image/jpeg", SafeHttp.mimeOf(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0, 0)))
        assertEquals("image/gif", SafeHttp.mimeOf("GIF89a".toByteArray()))
        assertEquals(
            "image/webp",
            SafeHttp.mimeOf("RIFF____WEBPVP8 ".toByteArray()),
            "WEBP 需要同时匹配 RIFF 与第 8 字节起的 WEBP"
        )

        // 非图片与未知格式都必须给 null —— 调用方据此回退到 image/png，而不是瞎猜
        assertNull(SafeHttp.mimeOf("<html>".toByteArray()))
        assertNull(SafeHttp.mimeOf(ByteArray(0)))
    }
}
