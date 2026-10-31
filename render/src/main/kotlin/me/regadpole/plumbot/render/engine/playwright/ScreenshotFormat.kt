// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.engine.playwright

/**
 * 卡片图输出格式：**不引入 WebP**，压缩只靠降 `scale_factor`，JPEG 仅作可选档。
 * 默认 PNG 是因为卡片上全是文字与细线，JPEG 的振铃会直接伤可读性。
 */
internal object ScreenshotFormat {

    const val PNG = "png"
    const val JPEG = "jpeg"

    /** JPEG 默认质量：85 在文字清晰度与体积之间通常是最优点。 */
    const val DEFAULT_QUALITY = 85

    /** 归一化配置值：接受 `jpeg` / `jpg` / `jpe`，其余（含 null）一律视为 PNG。 */
    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        JPEG, "jpg", "jpe" -> JPEG
        else -> PNG
    }

    /** 质量钳制到 1..100（Playwright 要求该区间；0/负数视为未配置，回退默认值）。 */
    fun quality(raw: Int): Int = if (raw <= 0) DEFAULT_QUALITY else raw.coerceIn(1, 100)
}
