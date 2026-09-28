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

/**
 * 群成员缓存抽象，结合 aedile 提供协程异步与 CompletableFuture 契约。
 */
interface GroupMemberCache {

    suspend fun getAsync(groupId: Long, userId: Long): MemberInfo?

    fun get(groupId: Long, userId: Long): CompletableFuture<MemberInfo?>

    suspend fun refreshAsync(groupId: Long)

    fun refresh(groupId: Long): CompletableFuture<Unit>

    fun invalidate(groupId: Long, userId: Long)

    fun invalidate(groupId: Long)

    fun invalidateAll()
}

/**
 * [GroupMemberCache] 的实现，基于 [com.sksamuel.aedile.core.LoadingCache]。
 *
 * @param fetchMember Adapter 提供的异步加载器
 */
open class DefaultGroupMemberCache(
    private val fetchMember: (groupId: Long, userId: Long) -> CompletableFuture<MemberInfo?>
) : GroupMemberCache {

    private val cache: LoadingCache<Pair<Long, Long>, MemberInfo> = Caffeine.newBuilder()
        .refreshAfterWrite(10.minutes.toJavaDuration())
        .expireAfterWrite(30.minutes.toJavaDuration())
        .asLoadingCache { (groupId, userId) ->
            try {
                val result = fetchMember(groupId, userId).await()
                result ?: throw NoSuchElementException("Member not found")
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
