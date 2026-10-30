// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 皮肤漏斗的纯函数部分：三级来源（皮肤插件 / GameProfile / 正版源）的产物统一是 **base64 纹理属性**，
 * 因此"属性 → 皮肤 URL"只有这一处解析，值得单独锁死。
 */
class AvatarResolverTest {

    private fun property(textureUrl: String): String {
        val json = """{"timestamp":1,"profileId":"x","textures":{"SKIN":{"url":"$textureUrl"}}}"""
        return Base64.getEncoder().encodeToString(json.toByteArray())
    }

    @Test
    fun `从纹理属性解析出皮肤地址`() {
        val url = AvatarResolver.companionSkinUrl(
            property("http://textures.minecraft.net/texture/abc123")
        )
        assertEquals("https://textures.minecraft.net/texture/abc123", url)
    }

    @Test
    fun `Mojang 返回的 http 会被升级为 https`() {
        // Mojang 实际返回的是 http，而抓取器只允许 https
        assertEquals(
            "https://textures.minecraft.net/texture/x",
            AvatarResolver.normalizeTextureUrl("http://textures.minecraft.net/texture/x")
        )
    }

    @Test
    fun `非 minecraft 域名的明文 http 被拒绝`() {
        assertNull(AvatarResolver.normalizeTextureUrl("http://evil.example/skin.png"))
        assertNull(
            AvatarResolver.companionSkinUrl(property("http://evil.example/skin.png")),
            "明文第三方地址不得进入抓取器"
        )
    }

    @Test
    fun `https 原样保留`() {
        assertEquals(
            "https://crafatar.com/avatars/x",
            AvatarResolver.normalizeTextureUrl("https://crafatar.com/avatars/x")
        )
    }

    @Test
    fun `Capes 等其它纹理不会被误当成皮肤`() {
        val json = """{"textures":{"CAPE":{"url":"https://textures.minecraft.net/texture/cape"}}}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray())
        assertNull(AvatarResolver.companionSkinUrl(encoded))
    }

    @Test
    fun `非法输入返回 null 而不是抛异常`() {
        assertNull(AvatarResolver.companionSkinUrl("not-base64!!"))
        assertNull(AvatarResolver.companionSkinUrl(""))
        assertNull(AvatarResolver.companionSkinUrl(Base64.getEncoder().encodeToString("not json".toByteArray())))
        assertNull(AvatarResolver.companionSkinUrl(Base64.getEncoder().encodeToString("{}".toByteArray())))
    }

    @Test
    fun `三级来源的缓存键互不相同以避免换源脏读`() {
        val keys = AvatarResolver.Source.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "来源键必须唯一")
        assertTrue("plugin" in keys && "profile" in keys && "premium" in keys)
    }

    @Test
    fun `逐级超时必须小于总预算`() {
        assertTrue(
            AvatarResolver.LEVEL_TIMEOUT_MS < AvatarResolver.TOTAL_BUDGET_MS,
            "单级超时若不小于总预算，漏斗就失去逐级降级的意义"
        )
    }
}
