// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.database

/**
 * 登录校验结果的进程内缓存（fail-closed 登录路径的提速层）。
 *
 * 缓存的是「玩家名 → 绑定 QQ」这一条结论：命中时登录路径完全不打数据库。写入者只有
 * `BindingLookupService`，失效由 `AbstractBindingDatabase` 的写方法负责 —— 缓存跟着真实表走，
 * 不存在「指令刚改完、登录还读到旧结果」的窗口。
 *
 * 上限 10 000 条按「单服最多两三千个玩家名 + 少量测试噪音」估的；超了按访问顺序淘汰最久未用的
 * 那条，登录高峰也不会把内存顶爆。
 */
internal object BindingLookupCache {

    /** 一条缓存结论；[userId] 为 null 表示「查过、确认未绑定」（负缓存）。 */
    data class Entry(val userId: String?, val storedAt: Long)

    /** 容量上限；超出后淘汰最久未访问的一条。 */
    const val CAPACITY = 10_000

    private val entries = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > CAPACITY
    }

    fun get(name: String): Entry? = synchronized(entries) { entries[keyOf(name)] }

    fun put(name: String, userId: String?, now: Long) {
        synchronized(entries) { entries[keyOf(name)] = Entry(userId, now) }
    }

    fun invalidate(name: String) {
        synchronized(entries) { entries.remove(keyOf(name)) }
    }

    /** 按 QQ 号失效：一个 QQ 可能绑了多个名字，写路径只知道其中一个。 */
    fun invalidateUser(userId: String) {
        synchronized(entries) { entries.entries.removeIf { it.value.userId == userId } }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    fun size(): Int = synchronized(entries) { entries.size }

    /** 与数据库侧一致：去空格 + 小写，避免同一玩家名出现两份缓存。 */
    fun keyOf(name: String): String = name.trim().lowercase()
}
