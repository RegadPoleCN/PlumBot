// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.LoadingCache
import com.sksamuel.aedile.core.asLoadingCache
import kotlinx.coroutines.future.await
import me.regadpole.plumbot.task.TaskProviderImpl
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** 通用缓存抽象：同时提供挂起函数与 `CompletableFuture` 两套契约（底层为 aedile 的 `LoadingCache`）。 */
interface BotCache<K : Any, V : Any> {

    suspend fun getAsync(key: K): V

    fun get(key: K): CompletableFuture<V>

    suspend fun refreshAsync(key: K)

    fun refresh(key: K): CompletableFuture<Unit>

    fun invalidate(key: K)

    fun invalidateAll()
}

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
