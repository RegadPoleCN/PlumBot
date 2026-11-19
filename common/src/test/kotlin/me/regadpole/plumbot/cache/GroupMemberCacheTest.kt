// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.cache

import kotlinx.coroutines.runBlocking
import me.regadpole.plumbot.api.bot.MemberInfo
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [DefaultGroupMemberCache] 的契约测试。
 *
 * "目标不在群内"时加载器回调 null：缓存层用非 null 哨兵做负缓存（不能把 null 直接交给
 * Caffeine——aedile 异步完成的 null future 会被最终一致地移除，回源次数不确定，且 absent
 * 结果完全没有缓存）。边界仍返回 null，异常路径不缓存。
 * 详见 coding-docs/review/1112/05-GroupMemberCacheTest-rca.md。
 */
class GroupMemberCacheTest {

    private val groupId = 100L
    private val userId = 2001L

    @Test
    fun `成员不在群内时返回 null 且负缓存生效`() = runBlocking {
        val calls = AtomicInteger()
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.completedFuture<MemberInfo?>(null)
        }

        assertNull(cache.getAsync(groupId, userId))
        // absent 是负缓存值：第二次查询命中缓存，不再回源
        assertNull(cache.getAsync(groupId, userId))
        assertEquals(1, calls.get())
    }

    @Test
    fun `不在群内的负缓存可被 invalidate 清除后重新回源`() = runBlocking {
        val calls = AtomicInteger()
        val member = MemberInfo(userId, "name", "card")
        var inGroup = false
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.completedFuture(if (inGroup) member else null)
        }

        assertNull(cache.getAsync(groupId, userId))
        assertEquals(1, calls.get())

        // 模拟进群事件触发的失效：此后应重新回源并拿到真实成员
        inGroup = true
        cache.invalidate(groupId, userId)
        assertEquals(member, cache.getAsync(groupId, userId))
        assertEquals(2, calls.get())
    }

    @Test
    fun `命中成员会被缓存`() = runBlocking {
        val calls = AtomicInteger()
        val member = MemberInfo(userId, "name", "card")
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.completedFuture(member)
        }

        assertEquals(member, cache.getAsync(groupId, userId))
        assertEquals(member, cache.getAsync(groupId, userId))
        assertEquals(1, calls.get())
    }

    @Test
    fun `加载器故障时仍返回 null 且不缓存`() = runBlocking {
        val calls = AtomicInteger()
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.failedFuture<MemberInfo?>(IllegalStateException("boom"))
        }

        assertNull(cache.getAsync(groupId, userId))
        assertNull(cache.getAsync(groupId, userId))
        assertEquals(2, calls.get())
    }

    @Test
    fun `put 后不触发回源 invalidate 后恢复回源`() = runBlocking {
        val calls = AtomicInteger()
        val member = MemberInfo(userId, "name", "card")
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.completedFuture(member)
        }

        cache.put(groupId, userId, member)
        assertEquals(member, cache.getAsync(groupId, userId))
        assertEquals(0, calls.get())

        cache.invalidate(groupId)
        assertEquals(member, cache.getAsync(groupId, userId))
        assertEquals(1, calls.get())
    }
}
