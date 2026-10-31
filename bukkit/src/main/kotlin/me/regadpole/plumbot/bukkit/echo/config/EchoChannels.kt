// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bukkit.echo.config

/** 远程回显通道的稳定标识，与 config.yml 中的字符串一一对应。 */
internal object EchoChannels {
    const val PAPER_FEEDBACK = "paper_feedback"
    const val PLUGIN_COMMAND = "plugin_command"
    const val CONSOLE = "console"
    const val RCON = "rcon"
    const val NMS = "nms"
    const val PROXY_SENDER = "proxy_sender"

    val KNOWN: Set<String> = linkedSetOf(
        PAPER_FEEDBACK,
        PLUGIN_COMMAND,
        CONSOLE,
        RCON,
        NMS,
        PROXY_SENDER
    )

    val DEFAULT_CHANNELS: List<String> = listOf(PAPER_FEEDBACK, PLUGIN_COMMAND, CONSOLE)
}
