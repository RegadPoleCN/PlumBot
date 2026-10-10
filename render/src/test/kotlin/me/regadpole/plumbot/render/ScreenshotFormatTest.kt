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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 卡片图输出格式（决策：不引入 WebP，走"降 `scale_factor` + 可选 JPEG 档"）。
 *
 * 断言三件事：默认必须是 PNG（文字清晰优先）、`jpg` 等写法要能识别成 JPEG、
 * 质量值必须落在 Playwright 要求的 1..100 内。
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
