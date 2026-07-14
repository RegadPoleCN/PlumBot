package me.regadpole.plumbot.bot

import me.regadpole.plumbot.api.bot.IBot
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.listener.BotHandler
import me.regadpole.plumbot.listener.DefaultBotHandler
import me.regadpole.plumbot.platform.PlatformContext
import java.util.concurrent.CompletableFuture

/**
 * Adapter 公共模板基类，封装 handler 创建、加载/卸载广播、缓存刷新、成员查询 fallback。
 */
abstract class AbstractBotAdapter : BotImpl {

    abstract val context: PlatformContext
    abstract override val metadata: BotAdapterMetadata

    override var handler: BotHandler? = null

    val groupMemberCache: DefaultGroupMemberCache by lazy { DefaultGroupMemberCache(::fetchMember) }
    private val groupNameCache = DefaultBotCache<Long, String>(::loadGroupName)

    override fun start(): IBot {
        handler = DefaultBotHandler(context, this)

        // 协议相关初始化（连接、登录校验等），子类实现。
        doStart()

        if (context.config.getBoolean("feature", "load", "enable")) {
            context.config.getLongList("groups").forEach { groupId ->
                sendMsg(true, groupId, Messages.load, context.config.getBoolean("feature", "load", "pic"))
            }
        }

        preloadGroupCaches()

        return this
    }

    override fun shutdown() {
        if (context.config.getBoolean("feature", "load", "enable")) {
            context.config.getLongList("groups").forEach { groupId ->
                if (context.config.getBoolean("feature", "load", "pic")) {
                    sendGroupPicWithText(groupId, Messages.unload)
                } else {
                    sendGroupMsg(groupId, Messages.unload)
                }
            }
        }

        handler = null
        groupMemberCache.invalidateAll()
        groupNameCache.invalidateAll()

        // 协议相关关闭逻辑，子类实现。
        doShutdown()
    }

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
    abstract override fun sendGroupPicWithText(targetId: Long, message: String)
    abstract override fun sendUserPicWithText(targetId: Long, message: String)

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
        /**
         * Adapter 层默认同步等待超时（毫秒）。
         *
         * 当前与 [DefaultGroupMemberCache] 的 10 秒超时保持一致；
         * 如需调整，请同步检查缓存层的默认实现。
         */
        const val DEFAULT_ADAPTER_TIMEOUT_MS = 10000L

        private const val DEFAULT_ADAPTER_TIMEOUT_SECONDS = DEFAULT_ADAPTER_TIMEOUT_MS / 1000
    }
}
