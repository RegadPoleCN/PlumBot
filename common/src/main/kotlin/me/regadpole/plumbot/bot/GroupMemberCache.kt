package me.regadpole.plumbot.bot

import com.sksamuel.aedile.core.LoadingCache
import com.sksamuel.aedile.core.cacheBuilder
import kotlinx.coroutines.future.await
import me.regadpole.plumbot.task.TaskProviderImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

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

    private val cache: LoadingCache<Pair<Long, Long>, MemberInfo> = cacheBuilder<Pair<Long, Long>, MemberInfo> {
        refreshAfterWrite = 10.minutes
        expireAfterWrite = 30.minutes
    }.build { (groupId, userId) ->
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
