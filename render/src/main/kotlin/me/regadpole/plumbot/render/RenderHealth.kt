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

package me.regadpole.plumbot.render

import me.regadpole.plumbot.api.PublicApi

/**
 * 渲染子系统的**健康计数快照**（分类口径的唯一真源）。
 *
 * **门户是渲染结果的唯一计数者**（它知道每次 render 究竟停在哪一步），业务层只计三件
 * 门户看不到的事 —— 配置关闭、引擎预热失败、图片发送失败。两套计数器一旦各记一遍，
 * 同一场故障就会漏计或双计，`/plumbot status` 上的数字便不可信。
 *
 * 各字段口径（除显式标注的包含关系外互不重叠）：
 * - [emitted]：成功产出并返回图片的次数；
 * - [degraded]：主引擎失败次数 —— **包含** [fallbackAwt] 与「最终无图」的情况；
 * - [fallbackAwt]：其中由 AWT 兜底补图成功的次数（⊂ [degraded]）；
 * - [failed]：最终没有拿到任何图片的次数（含载荷超限、驱动异常）；载荷超限发生在主引擎
 *   之前，因此**不**计入 [degraded]；引擎不可用造成的失败同时计入 [degraded] 与 [engineUnavailable]；
 * - [queueRejected]：容量已满、**根本没有尝试渲染**的次数（不算降级，算拒绝）；
 * - [engineUnavailable]：引擎未初始化 / 运行库尚未装载导致的失败次数（⊂ [degraded]）；
 * - [lastFailure]：最近一次失败的可读摘要（路由 + 原因，已截断）。
 *
 * 计数自**进程启动**起单调累积，/plumbot render reset 可清零。
 */
@PublicApi
data class RenderHealth(
    val emitted: Long = 0,
    val degraded: Long = 0,
    val fallbackAwt: Long = 0,
    val failed: Long = 0,
    val queueRejected: Long = 0,
    val engineUnavailable: Long = 0,
    val lastFailure: String? = null
) {
    companion object {
        /** 未实现该能力的门户返回的空快照。 */
        val EMPTY = RenderHealth()
    }
}
