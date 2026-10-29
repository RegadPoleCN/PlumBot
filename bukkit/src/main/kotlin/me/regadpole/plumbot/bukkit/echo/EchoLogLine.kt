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

package me.regadpole.plumbot.bukkit.echo

import me.regadpole.plumbot.utils.ComponentProjector

/**
 * 控制台日志行归一化。log4j2 直接拿到纯 message，JUL / latest.log 则可能带时间戳与
 * "[Server thread/INFO]:" 前缀；include_log_prefix=false 时剥掉，只留玩家真正看到的内容。
 * 无论配置如何，本插件自己的审计行与 Paper 的 "System chat: " 都必须过滤，否则远程控制台
 * 会把自己的安全日志当成指令回显。
 */
internal object EchoLogLine {

    private const val AUDIT_MARKER = "[PlumBot-Security] 远程控制台"
    private const val SYSTEM_CHAT = "System chat: "

    private val TIMESTAMP_PREFIX = Regex("^\\[\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\] \\[[^\\]]+]:\\s?")
    private val BRACKET_PREFIX = Regex("^\\[[^\\]]+]:\\s?")

    fun normalize(raw: String, includeLogPrefix: Boolean): String? {
        var text = ComponentProjector.ansiToSection(raw).replace("\r", "")
        if (text.contains(AUDIT_MARKER)) return null
        if (!includeLogPrefix) {
            text = TIMESTAMP_PREFIX.replace(text, "")
            text = BRACKET_PREFIX.replace(text, "")
        }
        if (text.startsWith(SYSTEM_CHAT)) {
            text = text.substring(SYSTEM_CHAT.length)
        }
        text = text.trim()
        return text.ifEmpty { null }
    }

    fun containsAudit(text: String): Boolean = text.contains(AUDIT_MARKER)
}