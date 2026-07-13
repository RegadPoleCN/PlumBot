package me.regadpole.plumbot.bot

import me.regadpole.plumbot.internal.cache.TimedValue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 群成员信息。
 *
 * @param userId 成员 QQ 号
 * @param name 昵称
 * @param card 群名片
 * @param role 角色，例如 owner/admin/member
 */
data class MemberInfo(
    val userId: Long,
    val name: String,
    val card: String,
    val role: String = "member"
)

/**
 * 群成员缓存抽象，屏蔽不同 Adapter 的实现差异。
 */
interface GroupMemberCache {

    /**
     * 异步获取指定群成员信息；未命中缓存时会调用 Adapter 提供的 fetchMember。
     */
    fun get(groupId: Long, userId: Long): CompletableFuture<MemberInfo?>

    /**
     * 刷新指定群的成员缓存。
     */
    fun refresh(groupId: Long): CompletableFuture<Unit>

    /**
     * 失效指定群成员的缓存条目。
     */
    fun invalidate(groupId: Long, userId: Long)

    /**
     * 失效指定群的所有成员缓存条目。
     */
    fun invalidate(groupId: Long)

    /**
     * 失效所有缓存条目。
     */
    fun invalidateAll()
}

/**
 * [GroupMemberCache] 的默认实现，基于 [ConcurrentHashMap]。
 *
 * @param fetchMember Adapter 提供的异步加载器
 * @param refreshAfterWriteMillis 缓存刷新周期，默认 10 分钟
 */
open class DefaultGroupMemberCache(
    private val fetchMember: (groupId: Long, userId: Long) -> CompletableFuture<MemberInfo?>,
    private val refreshAfterWriteMillis: Long = DEFAULT_REFRESH_MILLIS
) : GroupMemberCache {

    private val cache = ConcurrentHashMap<Pair<Long, Long>, TimedValue<MemberInfo?>>()

    override fun get(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        val key = groupId to userId
        val now = System.currentTimeMillis()
        val existing = cache[key]
        if (existing != null && now - existing.timestamp < refreshAfterWriteMillis) {
            return existing.future
        }
        val future = fetchMember(groupId, userId)
        cache[key] = TimedValue(future, now)
        return future
    }

    /**
     * 直接写入成员信息，常用于启动时批量预热。
     */
    fun put(groupId: Long, userId: Long, info: MemberInfo): CompletableFuture<MemberInfo?> {
        val future = CompletableFuture.completedFuture<MemberInfo?>(info)
        cache[groupId to userId] = TimedValue(future)
        return future
    }

    override fun refresh(groupId: Long): CompletableFuture<Unit> {
        cache.keys.removeAll { it.first == groupId }
        return CompletableFuture.completedFuture(Unit)
    }

    override fun invalidate(groupId: Long, userId: Long) {
        cache.remove(groupId to userId)
    }

    override fun invalidate(groupId: Long) {
        cache.keys.removeAll { it.first == groupId }
    }

    override fun invalidateAll() {
        cache.clear()
    }

    companion object {
        private const val DEFAULT_REFRESH_MILLIS = 10L * 60L * 1000L
        private const val DEFAULT_TIMEOUT_SECONDS = 10L

        /**
         * 顶层同步等待工具，所有公开同步方法必须使用该 10 秒超时。
         */
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
