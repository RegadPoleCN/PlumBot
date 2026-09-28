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

package me.regadpole.plumbot.utils

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component

/**
 * 跨平台通用的控制台回显捕获器，纯基于 JVM 和 Adventure 文本契约。
 * 天然兼顾纯 String 平台（如老 Spigot、未来异构游戏 Hytale）与原生 Adventure 平台（Paper、Velocity）。
 */
class OutputCapturingBuffer(
    private val forwardTo: Audience? = null,
    private val maxLength: Int = 1500
) : Audience {

    private val buffer = StringBuilder()
    private val ansiRegex = Regex("\u001B\\[[;\\d]*m")

    /** 通道 1：纯文本行输入（供所有非 Adventure 平台或传统 API 调用） */
    fun appendString(message: String) {
        val clean = message.replace(ansiRegex, "")
        buffer.appendLine(clean)
    }

    /** 通道 2：Adventure 富文本组件输入（供原生 Adventure 平台调用） */
    override fun sendMessage(message: Component) {
        val plain = message.toPlainText()
        buffer.appendLine(plain)
        forwardTo?.sendMessage(message)
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
