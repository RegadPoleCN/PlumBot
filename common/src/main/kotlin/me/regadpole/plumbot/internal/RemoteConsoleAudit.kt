// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

/**
 * 远程控制台执行的**审计文案**：高风险能力必须留痕，但完整指令可能带敏感参数（例如给玩家发
 * 密码、设置白名单理由），因此把"记什么"做成配置 `feature.remote_console.log_commands`
 * （默认 `true` = 记完整指令，保留事后审计能力）。抽成纯函数是为了让三端共用同一套文案与判断，
 * 也便于单测覆盖。
 */
object RemoteConsoleAudit {

    private const val PREFIX = "[PlumBot-Security] 远程控制台"

    /**
     * @param logCommands `true` 记完整指令；`false` 只记指令名与参数个数
     */
    fun describe(command: String, logCommands: Boolean): String {
        val trimmed = command.trim()
        if (logCommands) {
            return "$PREFIX 正在执行指令: /$trimmed"
        }
        val name = trimmed.substringBefore(' ').substringBefore('\t')
        val argCount = trimmed.split(Regex("\\s+")).count { it.isNotEmpty() } - 1
        return "$PREFIX 执行指令: $name（参数 $argCount 个，明细已按 log_commands=false 隐藏）"
    }
}
