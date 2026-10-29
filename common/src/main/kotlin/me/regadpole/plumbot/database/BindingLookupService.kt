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
package me.regadpole.plumbot.database

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 登录白名单查询：缓存 + 单飞 + 有界等待（fail-closed 登录路径的查询层）。
 *
 * 三个不变式：
 * 1. **命中缓存不打库**：正向条目 TTL 由 `feature.bind.login_cache_ttl_seconds` 决定，
 *    负向条目（确认未绑定）固定 30 秒，避免有人拿未绑定账号刷登录把库打满；
 * 2. **同一个名字并发登录只查一次**（[inflight] 单飞），几百人同时进服也只发一条 SQL；
 * 3. **等待有上限**：超过 `feature.bind.login_lookup_timeout_ms` 就返回
 *    [Outcome.UNAVAILABLE]，由调用方（`PlayerLoginService`）按 fail-closed 处理。
 *
 * 陈旧值复用是为了让「数据库抖动」不等于「所有人被踢」：正向条目过期后仍允许在
 * [STALE_GRACE_MS] 内直接放行，同时后台刷新；真正查不到（首次登录、负缓存过期、
 * 查询失败/超时）才走拒绝。
 */
internal object BindingLookupService {

    /** 一次登录校验的三种结论：绑定 / 未绑定 / 本次查不清。 */
    enum class Outcome { BOUND, NOT_BOUND, UNAVAILABLE }

    /** [resolve] 的返回值；[reason] 只在 [Outcome.UNAVAILABLE] 时有意义（用于日志）。 */
    data class LookupResult(val outcome: Outcome, val reason: String? = null)

    const val DEFAULT_TTL_SECONDS = 300L
    const val DEFAULT_TIMEOUT_MS = 500L
    const val MIN_TIMEOUT_MS = 50L
    const val MAX_TIMEOUT_MS = 5_000L

    /** 负缓存（确认未绑定）时长：短到不用等太久，长到能挡住刷登录。 */
    const val NEGATIVE_TTL_MS = 30_000L

    /** 正向条目过期后仍允许直接放行的窗口（同时后台刷新）。 */
    const val STALE_GRACE_MS = 60_000L

    /** 可替换时钟：TTL 与陈旧窗口都基于它，测试直接改这个即可。 */
    internal var clock: () -> Long = System::currentTimeMillis

    private val inflight = ConcurrentHashMap<String, CompletableFuture<Lookup>>()

    @Volatile
    private var scope: CoroutineScope = newScope()

    /** 保护 [scope] 的重建（[ensureRunning] 与 [resetForTests] 可能并发）。 */
    private val scopeLock = Any()

    fun resolve(playerName: String, ttlSeconds: Long, timeoutMs: Long): LookupResult {
        ensureRunning()
        val key = BindingLookupCache.keyOf(playerName)
        val now = clock()
        val cached = BindingLookupCache.get(key)
        if (cached != null) {
            val ttlMillis = if (cached.userId == null) {
                NEGATIVE_TTL_MS
            } else {
                ttlSeconds.coerceAtLeast(1L) * 1000L
            }
            val age = now - cached.storedAt
            if (age in 0 until ttlMillis) return cached.toResult()
            if (cached.userId != null && age in ttlMillis until (ttlMillis + STALE_GRACE_MS)) {
                startLoad(key)
                return LookupResult(Outcome.BOUND)
            }
        }
        val timeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        return when (val lookup = startLoad(key).await(timeout)) {
            is Lookup.Found -> lookup.toResult()
            is Lookup.Failed -> LookupResult(Outcome.UNAVAILABLE, lookup.reason)
            Lookup.Timeout -> LookupResult(Outcome.UNAVAILABLE, "等待数据库查询超过 ${timeout}ms")
        }
    }

    /**
     * 确保查询协程可用。
     *
     * [DatabaseProvider.shutdown] 会取消这个作用域（插件停用 / 重载），而重载后
     * `DatabaseProvider.start` 会在同一个 JVM 里再来一遍。作用域一旦永久处于取消状态，
     * 每次登录就只会等满 `login_lookup_timeout_ms` 再 fail-closed 拒绝 —— 这是实测出来的
     * 行为（未就绪用例拿到的原因是「等待超过 500ms」而不是「尚未就绪」）。
     * 入口处按需重建，代价只是一次 `isActive` 判断。
     */
    internal fun ensureRunning() {
        if (scope.coroutineContext[Job]?.isActive == true) return
        synchronized(scopeLock) {
            if (scope.coroutineContext[Job]?.isActive == true) return
            scope = newScope()
        }
    }

    /** 插件关闭时调用：取消查询协程，丢弃在途请求。 */
    fun shutdown() {
        scope.cancel()
        inflight.clear()
    }

    /** 仅供测试：清空缓存与在途请求，并换一个新的 scope（[shutdown] 之后服务仍可用）。 */
    internal fun resetForTests() {
        shutdown()
        BindingLookupCache.clear()
        synchronized(scopeLock) { scope = newScope() }
        clock = System::currentTimeMillis
    }

    /**
     * 单飞发起一次查询，返回调用方要等的 future。
     *
     * 两条细节都是踩出来的：
     * 1. future 必须**先出 map 再 complete**：反过来会留下一个「已完成但仍在 map 里」的条目，
     *    后来的调用方拿到它就会立刻得到一次**过期**的结论（负缓存到期后仍不重新查库，正是
     *    全量跑测试时偶发的那条失败）。
     * 2. 万一还是拿到了已完成的条目（上面的窗口在极端调度下仍可能存在），就把它摘掉重试一次。
     */
    private fun startLoad(key: String): CompletableFuture<Lookup> {
        while (true) {
            val created = CompletableFuture<Lookup>()
            val existing = inflight.putIfAbsent(key, created)
            if (existing == null) {
                scheduleLoad(key, created)
                return created
            }
            if (!existing.isDone) return existing
            inflight.remove(key, existing)
        }
    }

    private fun scheduleLoad(key: String, created: CompletableFuture<Lookup>) {
        try {
            scope.launch {
                val lookup = load(key)
                if (lookup is Lookup.Found) BindingLookupCache.put(key, lookup.userId, clock())
                inflight.remove(key, created)
                created.complete(lookup)
            }
        } catch (t: Throwable) {
            inflight.remove(key, created)
            created.complete(Lookup.Failed("无法调度数据库查询：${t.message ?: t.javaClass.simpleName}"))
        }
    }

    /** 唯一的数据库出入口；任何异常（含「数据库未就绪」）都折成 UNAVAILABLE。 */
    private fun load(key: String): Lookup = try {
        Lookup.Found(DatabaseProvider.getBindByName(key))
    } catch (t: Throwable) {
        Lookup.Failed(t.message ?: t.javaClass.simpleName)
    }

    private fun CompletableFuture<Lookup>.await(timeoutMs: Long): Lookup = try {
        get(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        Lookup.Timeout
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        Lookup.Failed("等待数据库查询被中断")
    } catch (e: ExecutionException) {
        Lookup.Failed(e.cause?.message ?: e.message ?: "等待数据库查询失败")
    } catch (e: Exception) {
        Lookup.Failed(e.message ?: e.javaClass.simpleName)
    }

    private fun newScope(): CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("PlumBot-BindingLookup"))

    private fun BindingLookupCache.Entry.toResult(): LookupResult =
        if (userId.isNullOrEmpty()) LookupResult(Outcome.NOT_BOUND) else LookupResult(Outcome.BOUND)

    private fun Lookup.Found.toResult(): LookupResult =
        if (userId.isNullOrEmpty()) LookupResult(Outcome.NOT_BOUND) else LookupResult(Outcome.BOUND)

    private sealed interface Lookup {
        class Found(val userId: String?) : Lookup
        class Failed(val reason: String) : Lookup
        data object Timeout : Lookup
    }
}
