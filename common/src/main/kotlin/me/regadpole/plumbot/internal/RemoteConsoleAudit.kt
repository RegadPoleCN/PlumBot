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

package me.regadpole.plumbot.internal

/**
 * 远程控制台执行的**审计文案**（决策 D9）。
 *
 * 背景：远程控制台是高风险能力，执行前必须在日志留痕；但完整指令可能带敏感参数
 * （例如给玩家发密码、设置白名单理由）。因此把"记什么"做成配置：
 * `feature.remote_console.log_commands`（默认 `true` = 记完整指令，保留事后审计能力）。
 *
 * 抽成纯函数是为了让三端共用同一套文案与判断，且可被单测覆盖。
 */
object RemoteConsoleAudit {

    private const val PREFIX = "[PlumBot-Security] 远程控制台"

    /**
     * 生成审计日志文案。
     *
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
