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
 * 准入判定：白名单只约束**起点**，跳转目标只要求 https。
 *
 * 背景：旧实现开 `instanceFollowRedirects = true`，跳转目标完全不受控；现在改为手动逐跳跟随，
 * 但只对起点查白名单 —— CDN / 镜像站 302 到自有域名是常态。
 * 同时不再解析目标域名：DNS 代理 / fake-ip 环境下解析结果与真实出口无关。
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
    fun `起点白名单不约束跳转目标`() {
        // CDN / 镜像站 302 到自有域名是常态，跳转目标只要仍是 https 就放行
        assertNotNull(SafeHttp.rejectionReason(URI("https://cdn.example.org/x.png"), allowed), "起点仍必须在白名单内")
        assertNull(SafeHttp.redirectRejectionReason(URI("https://cdn.example.org/x.png")))
    }

    @Test
    fun `跳转目标仍必须 https`() {
        val reason = SafeHttp.redirectRejectionReason(URI("http://cdn.example.org/x.png"))
        assertNotNull(reason)
        assertTrue(reason.contains("仅允许 https"), "拒绝原因应指明协议：$reason")
        // https 直连内网地址不再被审查：这是已知残余面，靠内容侧校验兜底
        assertNull(SafeHttp.redirectRejectionReason(URI("https://127.0.0.1/x.png")))
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
