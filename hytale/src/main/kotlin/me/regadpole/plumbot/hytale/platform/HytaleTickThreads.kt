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

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * "当前线程是不是 Hytale 的游戏 tick 线程"的判定表（主线程守卫用）。
 *
 * Hytale 没有 `Bukkit.isPrimaryThread()`，也没有 tick 线程查询 API：每个 World 一条
 * TickingThread（implements Executor），任务由 [HytalePlatformScheduler] 经
 * `world.execute(...)` 交给世界线程。所以只有入队那一刻知道谁是 tick 线程 ——
 * 调度器包装任务时 [markCurrentThreadIsTickThread]，调用方用 [isOnGameThread] 查表。
 *
 * 语义边界：命中 = 该线程至少跑过一次我们排进世界队列的任务；**未命中不等于**不是
 * tick 线程（池线程可能被回收，或还没跑过我们的任务），因此未命中只用于"允许下载"，
 * 绝不用于"证明安全"。池线程被复用时判定保留，是有意的：它依旧服务世界 tick。
 */
internal object HytaleTickThreads {

    private val tickThreads: MutableSet<Thread> =
        Collections.newSetFromMap(ConcurrentHashMap<Thread, Boolean>())

    /** 在**世界 tick 线程上**调用（由调度器的任务包装器调用），把当前线程登记为 tick 线程。 */
    fun markCurrentThreadIsTickThread() {
        tickThreads.add(Thread.currentThread())
    }

    /** 当前线程是否已被判定为游戏 tick 线程。 */
    fun isOnGameThread(): Boolean = tickThreads.contains(Thread.currentThread())

    /** 清空记账（仅供测试与插件停用后重置；生产路径不需要主动调用）。 */
    fun clear() {
        tickThreads.clear()
    }
}
