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

/**
 * 卡片图输出格式（决策：**不引入 WebP**，走"降 `scale_factor` + 可选 JPEG 档"）。
 *
 * 为什么默认 PNG：卡片上全是文字与细线，JPEG 的振铃会直接伤可读性 ——
 * 因此压缩优先靠"降低设备像素比"（面积减少 → 字节数近似线性下降），JPEG 只作为可选档。
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
