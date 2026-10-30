// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 卡片图输出格式：不引入 WebP，走"降 `scale_factor` + 可选 JPEG 档"。
 * 断言默认必须是 PNG（文字清晰优先）、`jpg` 等写法识别成 JPEG、质量值落在 1..100。
 */
class ScreenshotFormatTest {

    @Test
    fun `defaults to png when nothing is configured`() {
        assertEquals("png", ScreenshotFormat.normalize(null))
        assertEquals("png", ScreenshotFormat.normalize(""))
        assertEquals("png", ScreenshotFormat.normalize("   "))
    }

    @Test
    fun `accepts jpeg spellings and normalizes case`() {
        assertEquals("jpeg", ScreenshotFormat.normalize("jpeg"))
        assertEquals("jpeg", ScreenshotFormat.normalize("JPEG"))
        assertEquals("jpeg", ScreenshotFormat.normalize(" jpg "))
        assertEquals("jpeg", ScreenshotFormat.normalize("jpe"))
    }

    @Test
    fun `unknown formats fall back to png instead of failing`() {
        assertEquals("png", ScreenshotFormat.normalize("webp"), "不支持 WebP —— 必须安全回落到 PNG")
        assertEquals("png", ScreenshotFormat.normalize("bmp"))
    }

    @Test
    fun `quality is clamped to the range playwright accepts`() {
        assertEquals(85, ScreenshotFormat.quality(0), "未配置时用默认质量")
        assertEquals(85, ScreenshotFormat.quality(-10))
        assertEquals(1, ScreenshotFormat.quality(1))
        assertEquals(100, ScreenshotFormat.quality(100))
        assertEquals(100, ScreenshotFormat.quality(9_999), "超过 100 必须钳制，否则 Playwright 会抛错")
    }
}
