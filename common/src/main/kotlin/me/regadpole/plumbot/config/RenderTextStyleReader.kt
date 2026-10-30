// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.config

import me.regadpole.plumbot.render.RenderTextStyle

/**
 * 把 config.yml 的 `render.text` 段读成渲染门户使用的 [RenderTextStyle]。
 *
 * 单独成函数是为了可测：直接写在启动路径里时漏读一个键不会有任何反馈
 * （`max_line_width` 就曾因此完全不生效）。缺失或非法的值一律回落 [RenderTextStyle]
 * 默认值，与 render-protocol.json 的 `text` 节点保持一致。
 */
object RenderTextStyleReader {

    fun read(config: YamlConfigurator, defaults: RenderTextStyle = RenderTextStyle()): RenderTextStyle {
        val threshold = config.getDouble("render", "text", "contrast_threshold")
        // <=0（含用户显式写 0）表示不限制换行；缺失/非数字回落默认值。
        val maxLineWidth = config.getNumberOrNull("render", "text", "max_line_width")
            ?.toInt()
            ?.coerceAtLeast(0)
            ?: defaults.maxLineWidth
        return RenderTextStyle(
            baseColor = config.getString("render", "text", "base")?.takeIf { it.isNotBlank() }
                ?: defaults.baseColor,
            textColor = config.getString("render", "text", "color")?.takeIf { it.isNotBlank() }
                ?: defaults.textColor,
            contrastThreshold = threshold.takeIf { it.isFinite() && it > 0.0 }
                ?: defaults.contrastThreshold,
            autoOutline = config.getBoolean("render", "text", "auto_outline"),
            maxLineWidth = maxLineWidth
        )
    }
}
