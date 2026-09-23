package me.regadpole.plumbot.bot

import com.sksamuel.aedile.core.LoadingCache
import com.sksamuel.aedile.core.cacheBuilder
import kotlinx.coroutines.future.await
import me.regadpole.plumbot.task.TaskProviderImpl
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes

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

    private val cache: LoadingCache<K, V> = cacheBuilder<K, V> {
        refreshAfterWrite = 10.minutes
        expireAfterWrite = 30.minutes
    }.build { key ->
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
