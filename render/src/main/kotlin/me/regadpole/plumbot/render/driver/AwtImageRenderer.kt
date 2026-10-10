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

package me.regadpole.plumbot.render.driver

import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.ImageRenderer
import me.regadpole.plumbot.render.RenderOptions
import me.regadpole.plumbot.render.TextPayload
import me.regadpole.plumbot.render.TextToImg

internal class AwtImageRenderer(private val context: PlatformContext) : ImageRenderer {
    override val type: String = "awt"
    override val isSupported: Boolean get() = TextToImg.isSupported

    init {
        // 把 AWT 引擎的诊断输出接回平台日志体系，取代原本绕过日志的 System.err.println
        TextToImg.logSink = { message -> context.log(LogLevel.WARN, message) }
    }

    override suspend fun render(viewRoute: String, payload: Any, options: RenderOptions): ByteArray? {
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
