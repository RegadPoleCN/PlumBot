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

package me.regadpole.plumbot.hytale.platform

import me.regadpole.plumbot.api.platform.LogLevel

/**
 * Hytale 宿主控制台可用的级别：只保留 [HytalePlatformContext.log] 会分发的 5 档，
 * 不带 OFF/ALL/CONFIG 之类的管理级别，避免与 JUL 的开放集合混同。
 */
internal enum class HytaleConsoleLevel { FINEST, FINE, INFO, WARNING, SEVERE }

/** 一条已定级的日志：级别 + 最终落到宿主的文本 + 是否同时写 debug.log。 */
internal data class HytaleLogLine(
    val level: HytaleConsoleLevel,
    val text: String,
    val appendToDebugFile: Boolean
)

/**
 * 平台 [LogLevel] → Hytale 宿主日志的映射。
 *
 * 背景：HytaleLogger 默认只把 **INFO 及以上** 打到控制台，旧实现把 DEBUG/TRACE 映射成
 * atFine()，于是 debug.enable=true 打开后控制台仍一行调试日志都看不到。
 *
 * - debug 开启：DEBUG/TRACE 用 INFO 输出并加 [DEBUG] / [TRACE] 前缀，一眼能看出
 *   是调试级别而不是被降级的正常日志；
 * - debug 关闭：保持 FINE/FINEST，交给宿主级别设置决定，默认不可见但不污染控制台；
 * - DEBUG/TRACE 始终写入 debug.log（DebugProvider 未启用时自行忽略）。
 *
 * 刻意做成不依赖任何 Hytale 类型的纯函数，才能在 :hytale:test 里断言（宿主 jar 是
 * compileOnly，测试运行期不可见）。
 */
internal object HytaleLogMapping {

    fun map(level: LogLevel, message: String, debugEnabled: Boolean): HytaleLogLine = when (level) {
        LogLevel.TRACE -> if (debugEnabled) {
            HytaleLogLine(HytaleConsoleLevel.INFO, "[TRACE] $message", appendToDebugFile = true)
        } else {
            HytaleLogLine(HytaleConsoleLevel.FINEST, message, appendToDebugFile = true)
        }

        LogLevel.DEBUG -> if (debugEnabled) {
            HytaleLogLine(HytaleConsoleLevel.INFO, "[DEBUG] $message", appendToDebugFile = true)
        } else {
            HytaleLogLine(HytaleConsoleLevel.FINE, message, appendToDebugFile = true)
        }

        LogLevel.INFO -> HytaleLogLine(HytaleConsoleLevel.INFO, message, appendToDebugFile = false)
        LogLevel.WARN -> HytaleLogLine(HytaleConsoleLevel.WARNING, message, appendToDebugFile = false)
        LogLevel.ERROR,
        LogLevel.FATAL -> HytaleLogLine(HytaleConsoleLevel.SEVERE, message, appendToDebugFile = false)
    }
}
