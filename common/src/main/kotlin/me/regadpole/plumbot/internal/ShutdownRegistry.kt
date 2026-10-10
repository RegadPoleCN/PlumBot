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
 * 关闭钩子登记处 —— 兜住"忘了调用 shutdown"的组件。
 *
 * 背景：多处组件各自持有 IO 协程作用域（消息广播、词库重载等），但它们的拥有者分散在
 * 三个平台模块里，插件 `disable` 只显式关闭了 Bot/渲染/数据库/任务四类。
 * 漏关的作用域不会报错，只在反复 reload 时表现为线程数持续增长（R09）。
 *
 * 使用方式：**持有资源的一方在构造时登记自己**，`shutdown()` 时注销；
 * 插件禁用时统一 [runAll] 一次。显式关闭仍然保留 —— 登记只作为兜底，
 * 不能反过来成为"不写 shutdown"的借口。
 *
 * 实现注意：用**加锁的有序列表**而不是 `IdentityHashMap`。
 * 后者虽然提供引用相等语义，但**不保序**，无法保证"后创建的先释放"；
 * 而且它的 entry 是可变视图，清空映射后再遍历会读到 null key。
 */
object ShutdownRegistry {

    private val lock = Any()

    /** 按登记顺序保存（owner 用引用相等比较，避免不同实例互相覆盖）。 */
    private val hooks = mutableListOf<Pair<Any, () -> Unit>>()

    /**
     * 登记一个关闭钩子。
     *
     * 同一对象重复登记会被**覆盖**而不是堆积（例如同一组件被重建后又登记一次）。
     */
    fun register(owner: Any, hook: () -> Unit) {
        synchronized(lock) {
            hooks.removeAll { it.first === owner }
            hooks += owner to hook
        }
    }

    fun unregister(owner: Any) {
        synchronized(lock) {
            hooks.removeAll { it.first === owner }
        }
    }

    /**
     * 逆序执行所有钩子（后创建的先释放），逐个隔离异常：
     * 任何一个组件释放失败都不能阻止其余组件被回收。
     *
     * @return 执行失败的组件数量（0 表示全部成功）
     */
    fun runAll(): Int {
        val snapshot = synchronized(lock) {
            hooks.toList().also { hooks.clear() }
        }
        var failures = 0
        for ((owner, hook) in snapshot.asReversed()) {
            try {
                hook()
            } catch (t: Throwable) {
                failures++
                System.err.println(
                    "[PlumBot] 关闭钩子执行失败（${owner.javaClass.simpleName}）：${t.message ?: t.javaClass.simpleName}"
                )
            }
        }
        return failures
    }

    /** 当前登记数量（测试与排查用）。 */
    fun size(): Int = synchronized(lock) { hooks.size }

    /** 清空登记（测试用）。 */
    fun clear() {
        synchronized(lock) { hooks.clear() }
    }
}
