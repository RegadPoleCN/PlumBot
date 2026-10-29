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
 * 运行时健康计数（`/plumbot status`、bStats、故障排查）：静默失败在日志里翻不到，
 * 这里给出降级、过滤命中、队列拒绝、重连的量化状态。只增不减的计数器用 [AtomicLong]，
 * 读写都在业务热路径上，不能引入锁。
 */
object HealthCounters {

    /** 预定义键，避免各处拼字符串写错。 */
    enum class Key {
        /**
         * **引擎预热失败**（`warmUpEngines()` 返回 false）导致直接发纯文本的次数。
         * 渲染结果分类**只由门户**（`RenderPortal.renderHealth()`）回答；本键只保留门户
         * 看不到的失败——引擎根本没预热起来（旧实现混入发图失败与队列溢出，既漏计又双计）。
         */
        RENDER_FALLBACK,

        /** 按配置主动关闭图片（`render.outputs.<功能>=false` 或 `render.engine=none`）的次数。
         *  与 [RENDER_FALLBACK] 分开：两者都表现为"没图"，但前者是预期行为、后者是故障，
         *  混在一起会让降级计数虚高，真故障被淹没。 */
        RENDER_DISABLED,

        /** 图片已生成但**发送失败**（平台限流/连接断开）而回退纯文本的次数。
         *  与 [RENDER_FALLBACK] 分开：图是好的，问题在发送通道，排查方向完全不同。 */
        RENDER_SEND_FAILED,

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

        /**
         * **登录白名单查询不可用**（超时 / 数据库未就绪 / 查询异常）导致 fail-closed 拒绝的次数。
         * 与 [DB_INIT_FAILURES] 分开：初始化成功后数据库仍可能卡住（连接池耗尽、主库只读），
         * 这类故障只体现在登录拒绝上，混在一起会把排查方向从「登录路径」带到「启动路径」。
         */
        LOGIN_LOOKUP_UNAVAILABLE,
    }

    private val counters = ConcurrentHashMap<Key, AtomicLong>()

    fun increment(key: Key, delta: Long = 1L) {
        counters.computeIfAbsent(key) { AtomicLong() }.addAndGet(delta)
    }

    fun get(key: Key): Long = counters[key]?.get() ?: 0L

    /** 快照（排查与展示用）。 */
    fun snapshot(): Map<String, Long> =
        Key.entries.associate { it.name to get(it) }

    /**
     * 清零全部计数（`/plumbot render reset` 与测试）。渲染侧计数在门户里，两处都要调才不会
     * 出现"清了一半"的假象——由 `RenderCommandService` 负责成对调用。
     */
    fun reset() {
        counters.clear()
    }
}
