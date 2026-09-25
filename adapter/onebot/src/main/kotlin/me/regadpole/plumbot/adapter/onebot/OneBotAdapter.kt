package me.regadpole.plumbot.adapter.onebot

import com.google.gson.JsonArray
import kotlinx.coroutines.*
import me.regadpole.plumbot.api.config.Messages
import me.regadpole.plumbot.bot.AbstractBotAdapter
import me.regadpole.plumbot.bot.BotAdapterMetadata
import me.regadpole.plumbot.bot.BotImpl
import me.regadpole.plumbot.bot.MemberInfo
import me.regadpole.plumbot.internal.LogLevel
import me.regadpole.plumbot.listener.DefaultBotHandler
import me.regadpole.plumbot.platform.PlatformContext
import me.regadpole.plumbot.utils.TextToImg
import top.alazeprt.aonebot.action.GetGroupInfo
import top.alazeprt.aonebot.action.GetGroupMemberInfo
import top.alazeprt.aonebot.action.GetGroupMemberList
import top.alazeprt.aonebot.client.websocket.WebsocketBotClient
import top.alazeprt.aonebot.result.Group
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

class OneBotAdapter(
    override val context: PlatformContext,
    val client: WebsocketBotClient,
    override val metadata: BotAdapterMetadata
) : AbstractBotAdapter() {

    private val logger get() = context.logger
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isRunning = AtomicBoolean(false)
    private val isPreloaded = AtomicBoolean(false)

    @Volatile
    private var connectionMonitorJob: Job? = null

    override fun start(): BotImpl {
        handler = DefaultBotHandler(context, this)
        doStart()

        if (context.config.getBoolean("feature", "load", "enable")) {
            context.config.getLongList("groups").forEach { groupId ->
                sendMsg(true, groupId, Messages.load, context.config.getBoolean("feature", "load", "pic"))
            }
        }
        // 注意：不在此处同步调用 preloadGroupCaches()，由 WebSocket 连接成功状态机在连接就绪后异步触发！
        return this
    }

    override fun doStart() {
        isRunning.set(true)
        isPreloaded.set(false)
        client.registerEvent(OneBotListener(this))

        // 启动连接与健康监控循环
        startConnectionWorkflow()
    }

    override fun doShutdown() {
        isRunning.set(false)
        connectionMonitorJob?.cancel()
        scope.cancel()
        client.disconnect()
    }

    private fun startConnectionWorkflow() {
        scope.launch {
            try {
                logger.log(LogLevel.INFO, "[OneBot] 正在建立 WebSocket 连接...")
                client.connect()
            } catch (e: Exception) {
                logger.log(LogLevel.WARN, "[OneBot] 初始连接尝试失败: ${e.message}")
            }
            startConnectionMonitor()
        }
    }

    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch {
            var retryDelaySeconds = 5L
            while (isRunning.get() && isActive) {
                if (client.isConnected) {
                    // 连接就绪且首次/重连成功后触发预热
                    if (isPreloaded.compareAndSet(false, true)) {
                        logger.log(LogLevel.INFO, "[OneBot] WebSocket 连接已建立，开始后台异步预热群组与成员缓存...")
                        preloadGroupCaches()
                    }
                    retryDelaySeconds = 5L
                    delay(3000L) // 正常在线时，每隔 3 秒检测一次连接心跳
                } else {
                    isPreloaded.set(false)
                    logger.log(LogLevel.WARN, "[OneBot] 检测到 WebSocket 未处于连接状态，${retryDelaySeconds} 秒后尝试自动重连...")
                    delay(retryDelaySeconds * 1000L)

                    if (!isRunning.get()) break
                    try {
                        logger.log(LogLevel.INFO, "[OneBot] 正在尝试重新连接 WebSocket...")
                        client.connect()
                    } catch (e: Exception) {
                        logger.log(LogLevel.WARN, "[OneBot] 重连失败 (${e.message})")
                    }

                    // 指数退避：5s -> 10s -> 20s -> 40s -> 最大 60s
                    retryDelaySeconds = (retryDelaySeconds * 2).coerceAtMost(60L)
                }
            }
        }
    }

    override fun sendGroupMsg(targetId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(message))
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendUserMsg(targetId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(message))
        client.action(SendPrivateMessageSegments(targetId, segments))
    }

    override fun sendGroupPicWithText(targetId: Long, message: String) {
        val cleanText = stripColorCodes(message)
        if (!TextToImg.isSupported) {
            sendGroupMsg(targetId, cleanText)
            return
        }
        val segments = try {
            val bytes = TextToImg.toByteArray(message)
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        } catch (e: Exception) {
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(cleanText))
        }
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendUserPicWithText(targetId: Long, message: String) {
        val cleanText = stripColorCodes(message)
        if (!TextToImg.isSupported) {
            sendUserMsg(targetId, cleanText)
            return
        }
        val segments = try {
            val bytes = TextToImg.toByteArray(message)
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        } catch (e: Exception) {
            OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.text(cleanText))
        }
        client.action(SendPrivateMessageSegments(targetId, segments))
    }

    override fun sendGroupImage(targetId: Long, imageFile: File) {
        val bytes = imageFile.readBytes()
        val segments = OneBotSegmentBuilder.buildArray(OneBotSegmentBuilder.image(bytes))
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendGroupMsgAt(targetId: Long, userId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(
            OneBotSegmentBuilder.at(userId),
            OneBotSegmentBuilder.text(" $message")
        )
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun sendGroupMsgAtAll(targetId: Long, message: String) {
        val segments = OneBotSegmentBuilder.buildArray(
            OneBotSegmentBuilder.atAll(),
            OneBotSegmentBuilder.text(" $message")
        )
        client.action(SendGroupMessageSegments(targetId, segments))
    }

    override fun preloadGroupCaches() {
        context.config.getLongList("groups").forEach { groupId ->
            client.action(GetGroupMemberList(groupId)) { users ->
                users.forEach {
                    primeGroupMember(
                        groupId,
                        MemberInfo(it.member.userId, it.member.nickname, it.card, extractRole(it.role))
                    )
                }
            }
        }
    }

    override fun loadGroupName(groupId: Long): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        client.action(GetGroupInfo(groupId)) { group: Group ->
            try {
                future.complete(group.groupName)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
        val future = CompletableFuture<MemberInfo?>()
        client.action(GetGroupMemberInfo(groupId, userId)) { result ->
            try {
                future.complete(
                    MemberInfo(
                        result.member.userId,
                        result.member.nickname,
                        result.card,
                        extractRole(result.role)
                    )
                )
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    private fun extractRole(role: Any?): String {
        return try {
            role?.toString()
        } catch (_: Exception) {
            null
        } ?: "member"
    }
}
