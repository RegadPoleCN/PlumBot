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

    /** 缓存值允许为 null（成员不在群内 / 群不存在）：Caffeine 当作未命中，不写缓存、不记录
     *  异步加载异常，避免日志刷 "Exception thrown during asynchronous load"。 */
    private val cache: LoadingCache<Pair<Long, Long>, MemberInfo?> = Caffeine.newBuilder()
        .refreshAfterWrite(10.minutes.toJavaDuration())
        .expireAfterWrite(30.minutes.toJavaDuration())
        .asLoadingCache<Pair<Long, Long>, MemberInfo?> { (groupId, userId) ->
            try {
                fetchMember(groupId, userId).await()
            } catch (e: Throwable) {
                invalidate(groupId, userId)
                throw e
            }
        }

    override suspend fun getAsync(groupId: Long, userId: Long): MemberInfo? =
        try {
            cache.get(groupId to userId)
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
        cache.put(groupId to userId, info)
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
