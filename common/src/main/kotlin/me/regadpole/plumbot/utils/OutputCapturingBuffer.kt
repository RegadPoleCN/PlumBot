// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.utils

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike

/**
 * 跨平台通用的控制台回显捕获器，只依赖 JVM 与 Adventure 文本契约，因此纯 String 平台
 * 与原生 Adventure 平台都能用。
 *
 * 可保留颜色代码（§ 或 ANSI）：产物可直接交给渲染门户的 `console` 路由，生成带色彩的
 * 控制台回显卡片。
 */
class OutputCapturingBuffer(
    private val forwardTo: Audience? = null,
    private val maxLength: Int = DEFAULT_MAX_LENGTH,
    private val preserveColors: Boolean = true
) : Audience {

    companion object {
        /**
         * 捕获缓冲的**硬上限**（字符）。业务层还会按 `feature.remote_console.max_output_length`
         * 再截断一次；两者不联动时，配置写大就会出现「没到配置上限却已显示截断」的困惑，
         * 因此业务层把配置值钳制到本上限并给出一次性告警。
         */
        const val DEFAULT_MAX_LENGTH = 2000
    }

    private val buffer = StringBuilder()

    /** 通道 1：纯文本行输入（供所有非 Adventure 平台或传统 API 调用） */
    fun appendString(message: String) {
        // ANSI 是终端控制符，不能直接进群消息；先转成 Minecraft § 颜色。
        val sectionText = ComponentProjector.ansiToSection(message)
        val formatted = if (preserveColors) {
            sectionText
        } else {
            sectionText.stripMinecraftFormatting()
        }
        buffer.appendLine(formatted)
    }

    /** 通道 2：Adventure 富文本组件输入（供原生 Adventure 平台调用） */
    override fun sendMessage(message: Component) {
        val formatted = if (preserveColors) {
            ComponentProjector.project(message)
        } else {
            ComponentProjector.projectPlain(message)
        }
        buffer.appendLine(formatted)
        forwardTo?.sendMessage(message)
    }

    override fun sendMessage(message: ComponentLike) {
        sendMessage(message.asComponent())
    }

    /** 导出最终供群聊回复的文本（自动处理空输出与超长截断） */
    fun getResult(fallbackSuccessMessage: String = "指令已在主线程执行成功（无控制台回显）。"): String {
        val raw = buffer.toString().trim()
        if (raw.isBlank()) return fallbackSuccessMessage
        return if (raw.length > maxLength) {
            raw.substring(0, maxLength) + "\n... (输出已截断)"
        } else {
            raw
        }
    }
}