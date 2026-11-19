// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.LoadingCache
import com.sksamuel.aedile.core.asLoadingCache
import kotlinx.coroutines.future.await
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.task.TaskProviderImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** 群成员缓存：协程与 CompletableFuture 两套入口。 */
interface GroupMemberCache {

    suspend fun getAsync(groupId: Long, userId: Long): MemberInfo?

    fun get(groupId: Long, userId: Long): CompletableFuture<MemberInfo?>

    suspend fun refreshAsync(groupId: Long)

    fun refresh(groupId: Long): CompletableFuture<Unit>

    fun invalidate(groupId: Long, userId: Long)

    fun invalidate(groupId: Long)

    fun invalidateAll()
}

/** 基于 [com.sksamuel.aedile.core.LoadingCache] 的实现；加载器由适配层注入。 */
open class DefaultGroupMemberCache(
    private val fetchMember: (groupId: Long, userId: Long) -> CompletableFuture<MemberInfo?>
) : GroupMemberCache {

    /**
     * 内部缓存条目：[Present] 为真实成员，[Absent] 为"不在群内"的负缓存哨兵。
     *
     * 不能直接把 null 交给 Caffeine：aedile 的 loader future 永远在 IO 线程异步完成，而
     * Caffeine 对 null 完成的条目是**异步最终移除**（LocalAsyncCache#handleCompletion 在
     * 完成线程上 remove），第二次 get 可能抢在移除前复用同一个 null future——回源次数不确定，
     * 且"不在群内"完全没有负缓存，每次查询都打 bot API。用非 null 哨兵后行为确定化，
     * absent 结果同样享受 refreshAfterWrite / expireAfterWrite。
     * 详见 coding-docs/review/1112/05-GroupMemberCacheTest-rca.md。
     */
    private sealed interface Entry {
        data class Present(val info: MemberInfo) : Entry
        data object Absent : Entry
    }

    private val cache: LoadingCache<Pair<Long, Long>, Entry> = Caffeine.newBuilder()
        .refreshAfterWrite(10.minutes.toJavaDuration())
        .expireAfterWrite(30.minutes.toJavaDuration())
        .asLoadingCache<Pair<Long, Long>, Entry> { (groupId, userId) ->
            try {
                val info = fetchMember(groupId, userId).await()
                if (info == null) Entry.Absent else Entry.Present(info)
            } catch (e: Throwable) {
                // 故障不是"不在群内"：不缓存，下次查询重新回源。
                invalidate(groupId, userId)
                throw e
            }
        }

    override suspend fun getAsync(groupId: Long, userId: Long): MemberInfo? =
        try {
            when (val entry = cache.get(groupId to userId)) {
                is Entry.Present -> entry.info
                Entry.Absent -> null
            }
        } catch (_: NoSuchElementException) {
            null
        } catch (_: Throwable) {
            null
        }

    override fun get(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> =
        TaskProviderImpl.launchFuture {
            getAsync(groupId, userId)
        }

    fun put(groupId: Long, userId: Long, info: MemberInfo) {
        cache.put(groupId to userId, Entry.Present(info))
    }

    override suspend fun refreshAsync(groupId: Long) {
        invalidate(groupId)
    }

    override fun refresh(groupId: Long): CompletableFuture<Unit> =
        TaskProviderImpl.launchFuture {
            refreshAsync(groupId)
        }

    override fun invalidate(groupId: Long, userId: Long) {
        cache.invalidate(groupId to userId)
    }

    override fun invalidate(groupId: Long) {
        cache.underlying().synchronous().asMap().keys.removeIf { it.first == groupId }
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 10L

        @JvmStatic
        fun <T> await(
            future: CompletableFuture<T>,
            default: T,
            timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS
        ): T = try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Exception) {
            default
        }
    }
}
