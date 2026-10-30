// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
import me.regadpole.plumbot.bot.command.RenderCards
import me.regadpole.plumbot.render.RenderCard
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
     * 出图协程作用域：渲染是挂起能力，不能阻塞调用线程。
     *
     * **必须可重建**：`shutdown()` 会取消它；若沿用 lazy 单例，reload 后拿到的是**已取消**
     * 的作用域，所有出图都会以 `CancellationException` 静默失败。
     */
    @Volatile
    private var renderScope: CoroutineScope? = null

    /** 已派发的出图任务（关闭时有界排空用；任务结束自动移除）。 */
    private val pendingJobs = java.util.Collections.synchronizedSet(mutableSetOf<Job>())

    /** 取出图作用域，**仅供测试断言"作用域可重建"**：真正出图需要门户产出图片，测试无法稳定构造。 */
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

    /** 出图（群）：渲染已上移到本基类，适配层只负责把字节发出去。 */
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
            card = RenderCards.text(message),
            pic = true
        )
    }

    /** 按指定 route 出图；门户不可用或渲染失败时降级为 [fallbackText]。 */
    private fun dispatchRoute(
        isGroup: Boolean,
        targetId: Long,
        route: RenderRoute,
        card: RenderCard,
        pic: Boolean
    ) {
        // 降级文本只由卡片投影（含 title）；这里只去 § 色码，不能再用 stripColorCodes ——
        // 它还会删掉正文里字面量 "&a" 等序列，而 AWT/浏览器图片按原文显示，两边会不一致。
        val fallback = RenderCards.plain(card)
        val portal = if (pic) RenderProvider.getOrNull() else null
        if (portal == null) {
            // 平台没有渲染门户：属于"没有出图能力"，与用户配置关闭一起归入 DISABLED
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            sendSafely(isGroup, targetId, fallback)
            return
        }
        // 配置门控与"渲染失败"必须分开：门控关闭是预期行为，不能污染降级指标
        if (!portal.isImageEnabled(route.route)) {
            HealthCounters.increment(HealthCounters.Key.RENDER_DISABLED)
            context.log(LogLevel.DEBUG, "[PlumBot-Render] 路由 ${route.route} 未启用图片，按配置发送纯文本")
            sendSafely(isGroup, targetId, fallback)
            return
        }
        // 用 warmUpEngines（先触发按需装载）而不是 isImageCapable（只看当前是否可用）：
        // 否则"引擎尚未装载"会被误判为"不能出图"，按需装载路径永远走不到。
        if (!portal.warmUpEngines()) {
            // 已启用却拿不到引擎：这是故障，不是配置，必须计入降级并留下线索
            HealthCounters.increment(HealthCounters.Key.RENDER_FALLBACK)
            context.log(LogLevel.WARN, "[PlumBot-Render] 渲染引擎不可用（${route.route}），降级为纯文本")
            sendSafely(isGroup, targetId, fallback)
            return
        }

        val job = launchTracked {
            val result = try {
                portal.render(
                    RenderRequest(
                        route = route.route,
                        payload = card,
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
                // 这里**不再计数**：分类由门户 renderHealth() 负责，重复计数会让同一场故障出现两次。
                // 具体原因由门户记 WARN，此处只用 DEBUG 补一条路由线索，避免刷屏。
                val reason = (result as? RenderResult.Failed)?.reason
                context.log(
                    LogLevel.DEBUG,
                    "[PlumBot-Render] 渲染未产出图片（${route.route}）${reason?.let { ": $it" } ?: ""}，降级为纯文本"
                )
                sendSafely(isGroup, targetId, fallback)
                return@launchTracked
            }

            // 发送结果必须**同时**看异常与返回值：适配器可能吞掉异常，只看异常会让
            // "图没发出去"完全不可见；失败原因复用同一个变量，保证一次失败只留一条 WARN。
            val failure: String? = try {
                val ok = if (isGroup) sendGroupImage(targetId, png) else sendUserImage(targetId, png)
                if (ok) null else "适配器返回 false"
            } catch (t: Throwable) {
                // 门户契约"永不抛异常"不覆盖发送通道；这里把异常收敛为一次可读的失败。
                "${t.javaClass.simpleName}: ${t.message}"
            }

            if (failure != null) {
                // 图是好的、发送通道坏了：单列一类，避免与"渲染失败"混在一起
                HealthCounters.increment(HealthCounters.Key.RENDER_SEND_FAILED)
                context.log(
                    LogLevel.WARN,
                    "[PlumBot-Render] 图片未送达（${route.route}，${if (isGroup) "group" else "user"}:$targetId，原因: $failure），降级为纯文本"
                )
                sendSafely(isGroup, targetId, fallback)
            }
        }
    }

    /** 在出图作用域上派发任务并纳入关闭时的有界排空（抽出来让测试可直接驱动该路径）。 */
    private fun launchTracked(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job {
        val job = renderJobs().launch(block = block)
        pendingJobs += job
        job.invokeOnCompletion { pendingJobs -= job }
        return job
    }

    /** 仅供测试：用与生产**完全相同**的路径派发一个出图任务，验证有界排空。 */
    internal fun launchTrackedForTest(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        launchTracked(block)
    }

    /**
     * 有界排空：最多等待 [DRAIN_TIMEOUT_MS] 让已派发的出图任务结束，再取消作用域。
     *
     * 卸载广播也是异步任务，不等待就 `cancel()` 会丢掉通知；但无限等待会在引擎卡住时
     * 把平台关服线程一起拖死。
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

    /** 启动广播（`load` 路由），抽到基类供各适配器共用，避免同一逻辑散落两处。 */
    protected fun announceLoaded() {
        if (!context.config.getBoolean("feature", "load", "enable")) return
        val pic = true
        context.config.getLongList("groups").forEach { groupId ->
            dispatchRoute(
                isGroup = true,
                targetId = groupId,
                route = RenderRoute.LOAD,
                card = RenderCards.notice(Messages.load),
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
                card = RenderCards.notice(Messages.unload),
                pic = pic
            )
        }
    }

    private fun sendPlain(isGroup: Boolean, targetId: Long, text: String) {
        if (isGroup) sendGroupMsg(targetId, text) else sendUserMsg(targetId, text)
    }

    /**
     * 发送纯文本的**安全包装**：连接不可用时只记一条 WARN，不向调用方抛异常。
     *
     * OneBot 后端未启动时 `client.action()` 会抛 `WebsocketNotConnectedException`，
     * 异常从协程冒到宿主日志会看起来像插件崩溃；生命周期广播的语义是"发不出去就放弃"。
     */
    private fun sendSafely(isGroup: Boolean, targetId: Long, text: String) {
        try {
            sendPlain(isGroup, targetId, text)
        } catch (t: Exception) {
            context.log(
                LogLevel.WARN,
                "[PlumBot] 纯文本发送失败（${t.javaClass.simpleName}: ${t.message}），已跳过"
            )
        }
    }

    fun stripColorCodes(message: String): String =
        message.replace(COLOR_CODE_AMP, "").replace(COLOR_CODE_SECTION, "")

    override fun start(): IBot {
        transitionTo(BotState.CONNECTING)
        loadAnnounced = false
        handler = DefaultBotHandler(context, this)

        // 协议相关初始化（连接、登录校验等），子类实现。
        // 失败要落到 FAILED：否则状态会永远停在 CONNECTING，看起来像"还在连"。
        try {
            doStart()
        } catch (t: Throwable) {
            transitionTo(BotState.FAILED, "启动失败：${t.message}")
            throw t
        }

        // 同步适配器在 doStart 内 markOnline() 时已补发广播；异步适配器（OneBot）仍在
        // CONNECTING，由 markOnline() 在连接就绪后触发。此处无条件广播会在后端未启动时抛连接异常。
        if (botState == BotState.ONLINE && !loadAnnounced) {
            loadAnnounced = true
            announceLoaded()
        }

        preloadGroupCaches()

        return this
    }

    override fun shutdown() {
        // 只有"确实在线过"才广播关闭：从未连上（OneBot 后端未启动）时发送同样会抛
        // WebsocketNotConnectedException，且这条消息本来就没有任何接收者。
        val wasOnline = botState == BotState.ONLINE
        transitionTo(BotState.STOPPING)
        if (loadAnnounced && wasOnline) {
            announceUnloaded()
        }
        loadAnnounced = false

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

    @Volatile
    private var botState: BotState = BotState.INIT

    /** 本次启动是否已补发 load 广播：异步连接只在首次确认在线时广播，重连不重复。 */
    @Volatile
    private var loadAnnounced = false

    override val state: BotState get() = botState

    /** 状态迁移唯一入口；只在**变化时**记 INFO，避免热路径与状态查询把控制台刷满。 */
    protected fun transitionTo(next: BotState, detail: String? = null) {
        val previous = botState
        if (previous == next) return
        botState = next
        val suffix = detail?.let { "（$it）" } ?: ""
        context.log(LogLevel.INFO, "[PlumBot] Bot 状态：$previous → $next$suffix")
    }

    /** 子类在**确认连接可用**后调用（OneBot 在 WebSocket 就绪、同步适配器在完成初始化后）。 */
    protected fun markOnline() {
        val wasOnline = botState == BotState.ONLINE
        transitionTo(BotState.ONLINE)
        // 首次确认在线时才补发 load 广播：连接未就绪时 action() 会抛
        // WebsocketNotConnectedException（2026-10-18 Velocity/Hytale 实测）。
        if (!wasOnline && !loadAnnounced) {
            loadAnnounced = true
            announceLoaded()
        }
    }

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
     * 主动失效成员缓存，由 [BotEventDispatcher.dispatchUserDecrease] 统一调用。
     *
     * 放在中心分发点而不是各适配器监听器里，避免"某个适配器忘了失效"这类不对称。
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
     * 异步获取群成员信息，供 [groupMemberCache] 回源。
     *
     * **契约**：目标不在群内 / 群号无效时请让 future 以 `null` 正常完成，不要用异常表达 ——
     * 缓存层把 `null` 当"无值"，异常则会被视作异步加载失败并打印
     * `Exception thrown during asynchronous load`。
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
     * 刻意采用「有默认实现、且默认实现立即报错」而不是 `abstract`：旧的空实现会静默丢图，
     * 而把 `@StableApi` 的已有具体方法改成 `abstract` 会让第三方适配器升级时编译失败。
     *
     * 返回 `false` 与抛异常都会让基类回落纯文本并计入 [HealthCounters.Key.RENDER_SEND_FAILED]。
     */
    override fun sendGroupImage(targetId: Long, imageFile: java.io.File): Boolean {
        throw UnsupportedOperationException(
            "适配器 ${javaClass.simpleName} 未实现 sendGroupImage(targetId, imageFile)；" +
                "请在子类中实现图片直发，或移除 metadata.capabilities 中的 IMAGE_SEND。"
        )
    }

    /**
     * 直发原图（私聊）。与 [sendGroupImage] 同理：有默认实现，但默认实现立即报错。
     */
    override fun sendUserImage(targetId: Long, imageFile: java.io.File): Boolean {
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

        /** Adapter 层默认同步等待超时（毫秒），需与 [DefaultGroupMemberCache] 的 10 秒保持一致。 */
        const val DEFAULT_ADAPTER_TIMEOUT_MS = 10000L

        private const val DEFAULT_ADAPTER_TIMEOUT_SECONDS = DEFAULT_ADAPTER_TIMEOUT_MS / 1000

        /** 关闭时等待出图任务的上限，避免渲染卡死拖住平台关服线程。 */
        private const val DRAIN_TIMEOUT_MS = 2_500L
    }
}
