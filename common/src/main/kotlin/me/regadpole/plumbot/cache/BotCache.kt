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
import me.regadpole.plumbot.task.TaskProviderImpl
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * 通用缓存抽象，结合 [com.sksamuel.aedile.core.LoadingCache] 实现挂起函数与 CompletableFuture 双契约。
 */
interface BotCache<K : Any, V : Any> {

    suspend fun getAsync(key: K): V

    fun get(key: K): CompletableFuture<V>

    suspend fun refreshAsync(key: K)

    fun refresh(key: K): CompletableFuture<Unit>

    fun invalidate(key: K)

    fun invalidateAll()
}

/**
 * [BotCache] 的默认实现，基于 [com.sksamuel.aedile.core.LoadingCache]。
 */
open class DefaultBotCache<K : Any, V : Any>(
    private val loader: (K) -> CompletableFuture<V>
) : BotCache<K, V> {

    private val cache: LoadingCache<K, V> = Caffeine.newBuilder()
        .refreshAfterWrite(10.minutes.toJavaDuration())
        .expireAfterWrite(30.minutes.toJavaDuration())
        .asLoadingCache { key ->
            try {
                loader(key).await()
            } catch (e: Throwable) {
                invalidate(key)
                throw e
            }
        }

    override suspend fun getAsync(key: K): V = cache.get(key)

    override fun get(key: K): CompletableFuture<V> =
        TaskProviderImpl.launchFuture {
            getAsync(key)
        }

    override suspend fun refreshAsync(key: K) {
        cache.underlying().synchronous().refresh(key)
    }

    override fun refresh(key: K): CompletableFuture<Unit> =
        TaskProviderImpl.launchFuture {
            refreshAsync(key)
        }

    override fun invalidate(key: K) {
        cache.invalidate(key)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }
}
