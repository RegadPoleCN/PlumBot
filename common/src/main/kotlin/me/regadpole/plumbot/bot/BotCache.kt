package me.regadpole.plumbot.bot

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 通用异步缓存抽象，基于 [CompletableFuture] 与 [ConcurrentHashMap] 实现。
 */
interface BotCache<K, V> {

    /**
     * 获取指定键对应的值；未命中缓存时会调用加载器。
     */
    fun get(key: K): CompletableFuture<V>

    /**
     * 刷新指定键的缓存条目。
     */
    fun refresh(key: K): CompletableFuture<Unit>

    /**
     * 失效指定键的缓存条目。
     */
    fun invalidate(key: K)

    /**
     * 失效所有缓存条目。
     */
    fun invalidateAll()
}

/**
 * [BotCache] 的默认实现，基于 [ConcurrentHashMap]。
 *
 * @param loader 异步加载器
 * @param refreshAfterWriteMillis 缓存刷新周期，默认 10 分钟
 */
open class DefaultBotCache<K, V>(
    private val loader: (K) -> CompletableFuture<V>,
    private val refreshAfterWriteMillis: Long = DEFAULT_REFRESH_MILLIS
) : BotCache<K, V> {

    private data class TimedValue<V>(
        val future: CompletableFuture<V>,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = ConcurrentHashMap<K, TimedValue<V>>()

    override fun get(key: K): CompletableFuture<V> {
        val now = System.currentTimeMillis()
        val existing = cache[key]
        if (existing != null && now - existing.timestamp < refreshAfterWriteMillis) {
            return existing.future
        }
        val future = loader(key)
        cache[key] = TimedValue(future, now)
        return future
    }

    override fun refresh(key: K): CompletableFuture<Unit> {
        cache.remove(key)
        return CompletableFuture.completedFuture(Unit)
    }

    override fun invalidate(key: K) {
        cache.remove(key)
    }

    override fun invalidateAll() {
        cache.clear()
    }

    companion object {
        private const val DEFAULT_REFRESH_MILLIS = 10L * 60L * 1000L
    }
}
