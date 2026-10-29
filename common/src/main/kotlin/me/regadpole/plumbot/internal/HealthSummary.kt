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

import me.regadpole.plumbot.render.RenderHealth

/**
 * `/plumbot status` 的健康摘要行（纯排版，不做分类决策）。
 *
 * 抽成对象只为脱离平台上下文单测：分类口径只由 [RenderHealth]（门户）与 [HealthCounters]
 * （业务层）决定。字段顺序固定为：出图 / 降级 / 失败 → 配置关闭 / 预热失败 / 发图失败 →
 * 过滤 → Bot → 数据库。
 *
 * ## 口径（行尾必须自带）
 * 数字是**本次渲染之前**的快照：卡片正文先于渲染固化，门户在出图返回之后才计数，
 * 因此「降级 0」不能读成「这张图没降级」。`emitted` 还包含 AWT 兜底产出的图，
 * 所以「出图」也不等于「高清图」。
 */
internal object HealthSummary {

    /** 最近失败摘要的展示上限：它要进图片卡片的一行 footer，过长会折行穿版。 */
    private const val LAST_FAILURE_SHOWN_CHARS = 48

    fun line(
        render: RenderHealth,
        disabled: Long,
        warmUpFailures: Long,
        sendFailed: Long,
        filterHits: Long,
        filterReloadFailures: Long,
        botStarted: Long,
        botUnloaded: Long,
        botState: String,
        reconnects: Long,
        dbInitFailures: Long
    ): String = buildString {
        append("渲染 出图 ").append(render.emitted)
        append(" / 降级 ").append(render.degraded)
        if (render.fallbackAwt > 0) append("（AWT 兜底 ").append(render.fallbackAwt).append("）")
        append(" / 失败 ").append(render.failed)
        if (render.queueRejected > 0) append(" / 队列拒绝 ").append(render.queueRejected)
        if (render.engineUnavailable > 0) append(" / 引擎未就绪 ").append(render.engineUnavailable)
        append(" · 配置关闭 ").append(disabled)
        append(" · 预热失败 ").append(warmUpFailures)
        if (sendFailed > 0) append(" · 发图失败 ").append(sendFailed)
        append(" · 过滤命中 ").append(filterHits)
        append(" · 词库失败 ").append(filterReloadFailures)
        append(" · Bot 启停 ").append(botStarted).append('/').append(botUnloaded)
        append(" · 状态 ").append(botState)
        append(" · 重连 ").append(reconnects).append(" 次")
        append(" · 数据库失败 ").append(dbInitFailures)
        render.lastFailure?.let { failure ->
            append(" · 最近失败 ")
            append(if (failure.length <= LAST_FAILURE_SHOWN_CHARS) failure else failure.take(LAST_FAILURE_SHOWN_CHARS) + "…")
        }
        // 口径随行输出：读者看到数字时也看到它是快照，而不是本次渲染的实时值。
        append("（出图含兜底图 · 截至上一次出图，本次不计入）")
    }
}
