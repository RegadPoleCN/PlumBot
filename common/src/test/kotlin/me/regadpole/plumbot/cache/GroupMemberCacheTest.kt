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
 * "目标不在群内"时加载器回调 null：若缓存层把它当异常抛出，Caffeine 会打印
 * `Exception thrown during asynchronous load`（见 LocalAsyncCache#handleCompletion），
 * 让正常业务场景在日志里看起来像故障。现在 null 一律按"无值"处理。
 */
class GroupMemberCacheTest {

    private val groupId = 100L
    private val userId = 2001L

    @Test
    fun `成员不在群内时返回 null 且不抛异常`() = runBlocking {
        val calls = AtomicInteger()
        val cache = DefaultGroupMemberCache { _, _ ->
            calls.incrementAndGet()
            CompletableFuture.completedFuture<MemberInfo?>(null)
        }

        assertNull(cache.getAsync(groupId, userId))
        // null 是"无值"而非缓存值：不写缓存，下次查询会重新回源
        assertNull(cache.getAsync(groupId, userId))
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
