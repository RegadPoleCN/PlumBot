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

import java.io.File
import java.util.Base64

/**
 * 把服主配置的 TTF 注入浏览器页面，解决中文 / Emoji 的**豆腐块**问题（基线 E4）。
 *
 * ## 为什么用注入而不是内嵌模板字体
 *
 * 模板走 `vite-plugin-singlefile` 打包成**单文件**并在运行时下载。
 * 把中文字体内嵌进去会让模板体积从几十 KB 涨到数 MB —— 每次首装都要多下一次。
 *
 * 而服主为 AWT 引擎**已经配置过** `render.font`，把同一个字体注入页面：
 * 1. 不增加模板体积；
 * 2. **两条渲染途径字形完全一致** —— 这正是基线"共用同一套规则"的要求，
 *    否则同一条消息走 awt 与走 playwright 会呈现不同的字。
 *
 * 注入方式是 `@font-face` + data URL，配合 `!important` 覆盖模板自身的 font-family。
 */
internal object RenderFontInjector {

    /** 注入后统一使用的字体族名。 */
    const val FONT_FAMILY = "PlumBotRenderFont"

    sealed interface Outcome {
        data class Css(val text: String) : Outcome

        /** 不注入的原因（会以 WARN 记录一次，便于服主排查）。 */
        data class Skipped(val reason: String) : Outcome
    }

    /**
     * 生成注入用的 CSS。
     *
     * @param fontFile 服主配置的字体文件（与 AWT 引擎同一个来源）
     * @param maxBytes 体积上限：防止把几十 MB 的字体塞进每一页
     */
    fun buildCss(fontFile: File?, maxBytes: Int): Outcome {
        if (fontFile == null) return Outcome.Skipped("未配置 render.font，浏览器将使用系统字体")
        if (!fontFile.isFile) return Outcome.Skipped("字体文件不存在：${fontFile.path}")

        val size = fontFile.length()
        if (maxBytes > 0 && size > maxBytes) {
            return Outcome.Skipped(
                "字体体积 ${size / 1024 / 1024}MB 超过上限 ${maxBytes / 1024 / 1024}MB，已跳过注入（可调大 render.playwright.font_max_bytes）"
            )
        }

        val bytes = runCatching { fontFile.readBytes() }.getOrNull()
            ?: return Outcome.Skipped("字体文件不可读：${fontFile.path}")
        if (bytes.isEmpty()) return Outcome.Skipped("字体文件为空：${fontFile.path}")

        val format = fontFormat(fontFile.name)
        val base64 = Base64.getEncoder().encodeToString(bytes)

        val css = buildString {
            append("@font-face{font-family:'").append(FONT_FAMILY).append("';src:url(data:font/")
            append(format).append(";base64,").append(base64).append("') format('")
            append(if (format == "otf") "opentype" else "truetype")
            append("');font-display:block;}")
            // 覆盖模板自身的字体栈；Tailwind 的 font-sans 会作用在具体元素上，故需要 !important
            append("html,body,#app,#render-target,*{font-family:'").append(FONT_FAMILY)
            append("',ui-sans-serif,system-ui,'Noto Sans CJK SC','Microsoft YaHei',sans-serif !important;}")
        }
        return Outcome.Css(css)
    }

    private fun fontFormat(fileName: String): String = when {
        fileName.endsWith(".otf", ignoreCase = true) -> "otf"
        fileName.endsWith(".woff2", ignoreCase = true) -> "woff2"
        fileName.endsWith(".woff", ignoreCase = true) -> "woff"
        else -> "ttf"
    }
}
