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

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.StableApi

/**
 * Bot 的连接状态。
 *
 * ## 为什么用枚举而不是 `isConnected` 布尔值
 * 布尔值无法区分"已加载但正在重连"与"在线"：看到 `true` 就以为正常，实际消息可能发不出去。
 *
 * ```
 * INIT ──start()──▶ CONNECTING ──连接成功──▶ ONLINE
 *                      │                      │
 *                      │ 启动失败              │ 断线
 *                      ▼                      ▼
 *                    FAILED              RETRYING ──重连成功──▶ ONLINE
 *                                          │
 * 任意状态 ──shutdown()──▶ STOPPING ──▶ STOPPED（关闭过程异常则 FAILED）
 * ```
 */
@StableApi
enum class BotState {
    /** 已构造、尚未启动。 */
    INIT,

    /** 已开始启动/连接，但尚未确认可用。 */
    CONNECTING,

    /** 在线：可以收发消息。 */
    ONLINE,

    /** 连接断开，正在自动重连（此时**不应**当作可用）。 */
    RETRYING,

    /** 正在关闭。 */
    STOPPING,

    /** 已停止（正常关闭完成）。 */
    STOPPED,

    /** 启动或关闭过程失败。 */
    FAILED,
    ;

    /** 是否处于**可用**状态（唯一表示"能收发消息"的状态）。 */
    val isUsable: Boolean get() = this == ONLINE

    /** 是否处于**终态**（不会再自行变化，除非重新 start）。 */
    val isTerminal: Boolean get() = this == STOPPED || this == FAILED
}
