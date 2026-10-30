// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render.driver

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.ImageRenderer
import me.regadpole.plumbot.render.RenderCard
import me.regadpole.plumbot.render.RenderOptions
import me.regadpole.plumbot.render.TextPayload
import me.regadpole.plumbot.render.TextToImg

/**
 * 纯文本兜底驱动：不依赖浏览器，把载荷压成文本后用 [TextToImg] 画成图。
 *
 * 主引擎不可用、或路由不支持时的最后一层；表现力弱于模板，但永不缺席。
 */
internal class AwtImageRenderer(private val context: PlatformContext) : ImageRenderer {
    override val type: String = "awt"
    override val isSupported: Boolean get() = TextToImg.isSupported

    init {
        // AWT 引擎的诊断输出接回平台日志体系，不再直接 System.err.println
        TextToImg.logSink = { message -> context.log(LogLevel.WARN, message) }
    }

    override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? {
        // 统一卡片：正文是唯一真源，不对结构化载荷做字段转储。
        if (payload is RenderCard) {
            // AWT 没有模板那套版式，标题必须并进正文，否则 status 的服务器名、
            // kick/advancement 的标题只会出现在浏览器截图里，图文内容不一致。
            return TextToImg.toByteArray(payload.displayText)
        }
        // 通用文本路由：Title 与正文分层渲染，并支持 & 色码（控制台回显为 &-legacy）
        if (payload is TextPayload) {
            val text = if (payload.title.isNullOrBlank()) {
                payload.body
            } else {
                "${payload.title}\n${payload.body}"
            }
            return TextToImg.toByteArray(text, payload.ampersandCodes)
        }
        val textToDraw = formatPayload(viewRoute, payload)
        // 其他路径默认不启用 & 色码，避免把 "R&D" 这类正文误判为色码
        return TextToImg.toByteArray(textToDraw)
    }

    private fun formatPayload(viewRoute: String, payload: Any): String {
        if (payload is String) return payload
        if (payload is Map<*, *>) {
            val sb = StringBuilder("===== [${viewRoute.uppercase()}] =====\n")
            payload.forEach { (k, v) ->
                sb.appendLine("$k: $v")
            }
            return sb.toString().trim()
        }
        return payload.toString()
    }
}
