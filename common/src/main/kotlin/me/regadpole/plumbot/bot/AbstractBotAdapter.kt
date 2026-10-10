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

package me.regadpole.plumbot.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.regadpole.plumbot.api.StableApi
import me.regadpole.plumbot.api.bot.BotAdapterMetadata
import me.regadpole.plumbot.api.bot.BotHandler
import me.regadpole.plumbot.api.bot.BotState
import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.bot.MemberInfo
import me.regadpole.plumbot.cache.DefaultBotCache
import me.regadpole.plumbot.cache.DefaultGroupMemberCache
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.listener.DefaultBotHandler
import me.regadpole.plumbot.api.platform.LogLevel
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.render.RenderRequest
import me.regadpole.plumbot.render.RenderResult
import me.regadpole.plumbot.render.RenderRoute
import me.regadpole.plumbot.render.TextPayload
import java.util.concurrent.CompletableFuture

/**
 * Adapter 公共模板基类，封装 handler 创建、加载/卸载广播、缓存刷新、成员查询 fallback。
 *
 * **出图职责**：适配层不再自行渲染任何内容，统一向渲染门户索取 PNG 字节；
 * 门户不可用或渲染失败时自动降级为纯文本。
 */
@StableApi
abstract class AbstractBotAdapter : IBot {

    abstract val context: PlatformContext
    abstract override val metadata: BotAdapterMetadata

    override var handler: BotHandler? = null

    val groupMemberCache: DefaultGroupMemberCache by lazy { DefaultGroupMemberCache(::fetchMember) }
    private val groupNameCache = DefaultBotCache(::loadGroupName)

    /**
     * 出图协程作用域：渲染是挂起能力，不能阻塞调用线程（游戏事件线程/调度线程）。
     *
     * **必须可重建**：`shutdown()` 会取消它，若沿用"lazy 单例"写法，
     * 之后 `start()`（插件 reload / 重启 Bot）拿到的仍是**已取消**的作用域，
     * 所有出图都会以 `CancellationException` 静默失败。
     */
    @Volatile
    private var renderScope: CoroutineScope? = null

    /** 已派发的出图任务（关闭时有界排空用；任务结束自动移除）。 */
    private val pendingJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())

    /**
     * 取（必要时创建）出图作用域 —— **仅供测试断言"作用域可重建"**（A07）。
     *
     * 真正触发出图需要渲染门户返回图片，测试里无法稳定构造；而 A07 的契约正是
     * "shutdown 取消并置空、下次出图拿到新实例"，直接断言作用域本身才确定。
     */
    internal fun renderScopeForTest(): CoroutineScope = renderJobs()

    /** 当前作用域（未创建或已置空时为 null）—— 仅供测试。 */
    internal fun currentRenderScopeForTest(): CoroutineScope? = renderScope

    private fun renderJobs(): CoroutineScope {
        val current = renderScope
        if (current != null && current.isActive) return current
        return synchronized(this) {
            val again = renderScope
            if (again != null && again.isActive) again
            else CoroutineScope(Dispatchers.IO + SupervisorJob()).also { renderScope = it }
        }
    }

    override fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean) {
        if (message.isNullOrEmpty()) return
        if (isGroup) {
            if (isPic) sendGroupPicWithText(targetId, message)
            else sendGroupMsg(targetId, stripColorCodes(message))
        } else {
            if (isPic) sendUserPicWithText(targetId, message)
            else sendUserMsg(targetId, stripColorCodes(message))
        }
    }

    /**
     * 出图（群）。实现已上移到本基类：适配层只负责"把字节发出去"，不认识任何渲染细节。
     */
    override fun sendGroupPicWithText(targetId: Long, message: String) {
        dispatchRendered(isGroup = true, targetId = targetId, message = message)
    }

    /** 出图（私聊）。 */
    override fun sendUserPicWithText(targetId: Long, message: String) {
        dispatchRendered(isGroup = false, targetId = targetId, message = message)
    }

    private fun dispatchRendered(isGroup: Boolean, targetId: Long, message: String) {
        dispatchRoute(
            isGroup = isGroup,
            targetId = targetId,
            route = RenderRoute.TEXT,
            payload = TextPayload(body = message),
            fallbackText = stripColorCodes(message),
            pic = true
        )
    }

    /**
     * 按指定 route 出图；门户不可用或渲染失败时降级为 [fallbackText]。
     *
     * 适配层只负责"把字节发出去"，不认识任何渲染细节。
     */
    private fun dispatchRoute(
        isGroup: Boolean,
        targetId: Long,
        route: RenderRoute,
        payload: Any,
        fallbackText: String,
        pic: Boolean
    ) {
        val fallback = stripColorCodes(fallbackText)
        val portal = if (pic) RenderProvider.getOrNull() else null
        if (portal == null || !portal.isImageCapable) {
            sendPlain(isGroup, targetId, fallback)
            return
        }

        val job = launchTracked {
            val result = try {
                portal.render(
                    RenderRequest(
                        route = route.route,
                        payload = payload,
                        targetKey = (if (isGroup) "group:" else "user:") + targetId
                    )
                )
            } catch (t: Throwable) {
                // 门户契约是"永不抛异常"，此处再兜一层
                context.log(LogLevel.WARN, "[PlumBot-Render] 渲染异常（${route.route}）: ${t.message}")
                RenderResult.Failed(t.message ?: "exception")
            }

            val png = (result as? RenderResult.Image)?.png
            if (png == null) {
                // 渲染失败会静默降级为纯文本：功能仍"可用"，只是悄悄没图，必须计数才看得出来
                HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
                sendPlain(isGroup, targetId, fallback)
                return@launchTracked
            }

            runCatching {
                if (isGroup) sendGroupImage(targetId, png) else sendUserImage(targetId, png)
            }.onFailure {
                context.log(LogLevel.WARN, "[PlumBot-Render] 图片发送失败: ${it.message}，降级为纯文本")
                sendPlain(isGroup, targetId, fallback)
            }
        }
    }

    /**
     * 在出图作用域上派发任务，并**纳入关闭时的有界排空**。
     *
     * 抽出来是为了让"跟踪与排空"这条路径可被测试直接驱动（见 `launchTrackedForTest`）：
     * 否则要构造一个会成功出图的门户，测试既脆弱又难写。
     */
    private fun launchTracked(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job {
        val job = renderJobs().launch(block = block)
        pendingJobs += job
        job.invokeOnCompletion { pendingJobs -= job }
        return job
    }

    /** 仅供测试：用与生产**完全相同**的路径派发一个出图任务（验证 A10 的有界排空）。 */
    internal fun launchTrackedForTest(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        launchTracked(block)
    }

    /**
     * 有界排空：等待已派发的出图任务结束（最多 [DRAIN_TIMEOUT_MS]），再取消作用域。
     *
     * 关闭时的"卸载广播"也是异步出图任务；若不等待就 `cancel()`，
     * 刚派发的卸载通知会被直接取消 —— 表现为"停服公告有时发得出去、有时发不出去"。
     * 等待必须是**有界**的：渲染引擎卡住时不能把平台关服线程一起拖死。
     */
    private fun drainPendingJobs() {
        val jobs = synchronized(pendingJobs) { pendingJobs.toList() }
        if (jobs.isEmpty()) return
        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
        for (job in jobs) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) {
                context.log(LogLevel.WARN, "[PlumBot] 出图任务排空超时，剩余任务将被取消。")
                return
            }
            runCatching {
                runBlocking { withTimeoutOrNull(remaining) { job.join() } }
            }
        }
    }

    /**
     * 启动广播（`load` 路由）。
     *
     * 抽到基类供各适配器共用 —— 旧实现由 `OneBotAdapter.start()` **复制了一份**相同逻辑，
     * 任何改动都必须在两处同步，属遗漏高发点。
     */
    protected fun announceLoaded() {
        if (!context.config.getBoolean("feature", "load", "enable")) return
        val pic = true
        context.config.getLongList("groups").forEach { groupId ->
            dispatchRoute(
                isGroup = true,
                targetId = groupId,
                route = RenderRoute.LOAD,
                payload = mapOf("text" to Messages.load),
                fallbackText = Messages.load,
                pic = pic
            )
        }
    }

    /** 关闭广播（`unload` 路由）。 */
    protected fun announceUnloaded() {
        if (!context.config.getBoolean("feature", "load", "enable")) return
        val pic = true
        context.config.getLongList("groups").forEach { groupId ->
            dispatchRoute(
                isGroup = true,
                targetId = groupId,
                route = RenderRoute.UNLOAD,
                payload = mapOf("text" to Messages.unload),
                fallbackText = Messages.unload,
                pic = pic
            )
        }
    }

    private fun sendPlain(isGroup: Boolean, targetId: Long, text: String) {
        if (isGroup) sendGroupMsg(targetId, text) else sendUserMsg(targetId, text)
    }

    fun stripColorCodes(message: String): String =
        message.replace(COLOR_CODE_AMP, "").replace(COLOR_CODE_SECTION, "")

    override fun start(): IBot {
        transitionTo(BotState.CONNECTING)
        handler = DefaultBotHandler(context, this)

        // 协议相关初始化（连接、登录校验等），子类实现。
        // 失败要落到 FAILED：否则状态会永远停在 CONNECTING，看起来像"还在连"。
        try {
            doStart()
        } catch (t: Throwable) {
            transitionTo(BotState.FAILED, "启动失败：${t.message}")
            throw t
        }

        announceLoaded()

        preloadGroupCaches()

        return this
    }

    override fun shutdown() {
        transitionTo(BotState.STOPPING)
        announceUnloaded()

        // 卸载广播是异步出图任务：先有界等待它们结束，再回收作用域，
        // 否则刚派发的通知会被 cancel 掉。
        drainPendingJobs()

        // 先让处理器释放它自己的资源（内置服务的协程作用域），再断开引用；
        // 顺序反了会丢掉唯一入口，那些作用域就再也无法回收。
        handler?.let { current ->
            runCatching { current.shutdown() }
                .onFailure { context.log(LogLevel.WARN, "[PlumBot] 处理器释放失败：${it.message}") }
        }
        handler = null
        // 取消并置空：下次 start() 会拿到新作用域（渲染能力随 Bot 一起恢复）
        synchronized(this) {
            renderScope?.cancel()
            renderScope = null
        }
        groupMemberCache.invalidateAll()
        groupNameCache.invalidateAll()

        // 协议相关关闭逻辑，子类实现。
        // 关闭异常同样要落到 FAILED（而不是停在 STOPPING），否则状态会永远"正在关闭"。
        try {
            doShutdown()
            transitionTo(BotState.STOPPED)
        } catch (t: Throwable) {
            transitionTo(BotState.FAILED, "关闭失败：${t.message}")
            throw t
        }
    }

    // ---------- 连接状态机（R02）----------

    @Volatile
    private var botState: BotState = BotState.INIT

    override val state: BotState get() = botState

    /**
     * 状态迁移（唯一入口）。
     *
     * 只在**变化时**记录一行 INFO —— 状态查询会出现在热路径与状态展示里，
     * 每次调用都打日志会把控制台刷满。
     */
    protected fun transitionTo(next: BotState, detail: String? = null) {
        val previous = botState
        if (previous == next) return
        botState = next
        val suffix = detail?.let { "（$it）" } ?: ""
        context.log(LogLevel.INFO, "[PlumBot] Bot 状态：$previous → $next$suffix")
    }

    /** 子类在**确认连接可用**后调用（OneBot 在 WebSocket 就绪、同步适配器在完成初始化后）。 */
    protected fun markOnline() = transitionTo(BotState.ONLINE)

    /** 子类在检测到断线并进入重连循环时调用。 */
    protected fun markRetrying(reason: String? = null) = transitionTo(BotState.RETRYING, reason)

    /** 子类在不可恢复的失败时调用（例如凭据无效、依赖缺失）。 */
    protected fun markFailed(reason: String? = null) = transitionTo(BotState.FAILED, reason)

    override fun getGroupName(groupId: Long): String {
        val future = groupNameCache.get(groupId)
        return awaitWithTimeout(future, groupId.toString())
    }

    override fun checkUserInGroup(userId: Long, groupId: Long): Boolean {
        return awaitWithTimeout(
            groupMemberCache.get(groupId, userId).thenApply { it != null },
            false
        )
    }

    /**
     * 主动失效成员缓存。
     *
     * 由 [BotEventDispatcher.dispatchUserDecrease] 统一调用 —— 放在中心分发点而不是各适配器的
     * 监听器里，是为了避免"某个适配器忘了失效"这类不对称：历史上 OneBot 的监听器失效了缓存，
     * 而两个 MiraiMC 监听器没有，导致同一份代码在两个适配器上行为不同。
     */
    override fun invalidateGroupMember(groupId: Long, userId: Long) {
        groupMemberCache.invalidate(groupId, userId)
    }

    override fun getGroupUserName(groupId: Long, targetId: Long): String {
        val fallback = fallbackMemberInfo(targetId)
        val info = awaitWithTimeout(
            groupMemberCache.get(groupId, targetId).thenApply { it ?: fallback },
            fallback
        )
        return info.name.takeIf { it.isNotBlank() } ?: targetId.toString()
    }

    override fun getGroupUserCard(groupId: Long, targetId: Long): String {
        val fallback = fallbackMemberInfo(targetId)
        val info = awaitWithTimeout(
            groupMemberCache.get(groupId, targetId).thenApply { it ?: fallback },
            fallback
        )
        return info.card.takeIf { it.isNotBlank() }
            ?: info.name.takeIf { it.isNotBlank() }
            ?: targetId.toString()
    }

    /**
     * 刷新指定群的缓存；基于 CompletableFuture 实现。
     */
    open fun refreshCache(groupId: Long): CompletableFuture<Unit> {
        groupNameCache.invalidate(groupId)
        return groupMemberCache.refresh(groupId)
    }

    /**
     * 协议相关启动逻辑，例如建立连接或校验登录状态。
     */
    protected abstract fun doStart()

    /**
     * 协议相关关闭逻辑，例如断开连接。
     */
    protected abstract fun doShutdown()

    /**
     * 预加载群成员与群名称缓存。
     */
    protected abstract fun preloadGroupCaches()

    /**
     * 预热单条群成员缓存，供子类 [preloadGroupCaches] 复用，统一缓存写入路径。
     */
    protected fun primeGroupMember(groupId: Long, info: MemberInfo) {
        groupMemberCache.put(groupId, info.userId, info)
    }

    /**
     * 异步获取指定群成员信息，供 [groupMemberCache] 回源。
     */
    protected abstract fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?>

    /**
     * 异步加载群名称，供 [groupNameCache] 回源。
     */
    protected abstract fun loadGroupName(groupId: Long): CompletableFuture<String>

    abstract override fun sendGroupMsg(targetId: Long, message: String)
    abstract override fun sendUserMsg(targetId: Long, message: String)

    /**
     * 直发原图（群）。
     *
     * 这里刻意采用「**有默认实现、且默认实现立即报错**」，而不是 `abstract`：
     * - 旧的空实现会**静默丢弃**图片，是最坏的行为，必须避免；
     * - 但 `AbstractBotAdapter` 是 `@StableApi`，把已有具体方法改成 `abstract`
     *   会让第三方适配器在升级时直接编译失败。
     *
     * 默认实现立即抛出，既保证"绝不静默丢图"，又保住已有子类的源码兼容。
     */
    override fun sendGroupImage(targetId: Long, imageFile: java.io.File) {
        throw UnsupportedOperationException(
            "适配器 ${javaClass.simpleName} 未实现 sendGroupImage(targetId, imageFile)；" +
                "请在子类中实现图片直发，或移除 metadata.capabilities 中的 IMAGE_SEND。"
        )
    }

    /**
     * 直发原图（私聊）。与 [sendGroupImage] 同理：有默认实现，但默认实现立即报错。
     */
    override fun sendUserImage(targetId: Long, imageFile: java.io.File) {
        throw UnsupportedOperationException(
            "适配器 ${javaClass.simpleName} 未实现 sendUserImage(targetId, imageFile)；" +
                "请在子类中实现私聊图片直发，或移除 metadata.capabilities 中的 IMAGE_SEND。"
        )
    }

    override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) {
        sendGroupMsg(targetId, "@$userId $message")
    }
    override fun sendGroupMsgAtAll(targetId: Long, message: String) {
        sendGroupMsg(targetId, "@全体成员 $message")
    }

    /**
     * 统一同步等待入口，所有需要阻塞等待 Adapter 异步结果的地方应使用此方法。
     */
    protected open fun <T> awaitWithTimeout(future: CompletableFuture<T>, default: T): T {
        return DefaultGroupMemberCache.await(future, default, DEFAULT_ADAPTER_TIMEOUT_SECONDS)
    }

    /**
     * 构造成员信息 fallback，避免在多处重复创建相同的占位对象。
     */
    private fun fallbackMemberInfo(userId: Long) =
        MemberInfo(userId, userId.toString(), userId.toString())

    companion object {
        private val COLOR_CODE_AMP = Regex("&([0-9a-fklmnor])")
        private val COLOR_CODE_SECTION = Regex("§([0-9a-fklmnor])")

        /**
         * Adapter 层默认同步等待超时（毫秒）。
         *
         * 当前与 [DefaultGroupMemberCache] 的 10 秒超时保持一致；
         * 如需调整，请同步检查缓存层的默认实现。
         */
        const val DEFAULT_ADAPTER_TIMEOUT_MS = 10000L

        private const val DEFAULT_ADAPTER_TIMEOUT_SECONDS = DEFAULT_ADAPTER_TIMEOUT_MS / 1000

        /** 关闭时等待出图任务的上限，避免渲染卡死拖住平台关服线程。 */
        private const val DRAIN_TIMEOUT_MS = 2_500L
    }
}
