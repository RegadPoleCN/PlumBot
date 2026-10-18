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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 运行时健康计数 —— 供 `/plumbot status`、bStats 与故障排查使用。
 *
 * ## 为什么需要
 * 出问题时只有"日志里翻"，没有可一眼看出的量化状态：渲染降级了多少次、
 * 过滤命中多少次、队列拒绝多少次、重连多少次。这些都是"静默失败"最容易藏身的地方
 * （例如渲染回退到纯文本时功能仍在工作，只是悄悄没图）。
 *
 * 只增不减的计数器用 [AtomicLong]：读写都在业务热路径上，不能引入锁。
 */
object HealthCounters {

    /** 预定义键，避免各处拼字符串写错。 */
    enum class Key {
        /** 渲染降级为纯文本（引擎失败/超时/队列溢出等**启用后**的失败）。 */
        RENDER_FALLBACK,

        /**
         * 按配置主动关闭图片、直接发纯文本的次数（`feature.img.views.<route>.pic=false`
         * 或 `feature.img.engine=none`）。
         *
         * 与 [RENDER_FALLBACK] 分开计：两者都表现为"没图"，但前者是预期行为、后者是故障。
         * 混在一起会让降级计数长期虚高，真故障被淹没。
         */
        RENDER_DISABLED,

        /** 因队列容量耗尽被拒绝的渲染请求。 */
        RENDER_QUEUE_REJECTED,

        /** 敏感词命中（拦截或替换）。 */
        FILTER_HITS,

        /** 词库重载失败。 */
        FILTER_RELOAD_FAILURES,

        /** Bot 启动成功次数。 */
        BOT_STARTED,

        /** Bot 非正常结束/卸载次数。 */
        BOT_UNLOADED,

        /** 断线重连尝试次数（各适配器在重连回调处递增）。 */
        BOT_RECONNECTS,

        /** 数据库初始化失败次数。 */
        DB_INIT_FAILURES,
    }

    private val counters = ConcurrentHashMap<Key, AtomicLong>()

    fun increment(key: Key, delta: Long = 1L) {
        counters.computeIfAbsent(key) { AtomicLong() }.addAndGet(delta)
    }

    fun get(key: Key): Long = counters[key]?.get() ?: 0L

    /** 快照（排查与展示用）。 */
    fun snapshot(): Map<String, Long> =
        Key.entries.associate { it.name to get(it) }

    /** 重置（测试用；生产不调用）。 */
    fun reset() {
        counters.clear()
    }
}
