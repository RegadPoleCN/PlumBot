package me.regadpole.plumbot.bot

/**
 * 新增 Adapter 的代码模板与说明。
 *
 * 本 object 仅作为文档/模板存在，内部字符串不需要编译通过，也不应引入任何具体协议 SDK。
 */
object BotAdapterTemplate {

    val FACTORY_TEMPLATE = """
        package com.example.plumbot.adapter.myadapter

        import me.regadpole.plumbot.api.bot.IBot
        import me.regadpole.plumbot.bot.BotAdapterMetadata
        import me.regadpole.plumbot.bot.BotCapability
        import me.regadpole.plumbot.bot.BotFactory
        import me.regadpole.plumbot.platform.PlatformContext
        import me.regadpole.plumbot.platform.PlatformType

        object MyAdapterFactory : BotFactory {
            override val metadata = BotAdapterMetadata(
                type = "myadapter",
                displayName = "MyAdapter",
                // 必须声明支持的 PlatformType，否则不会被注册到当前平台
                supportedPlatforms = setOf(PlatformType.BUKKIT),
                // 若协议依赖外部插件（如 MiraiMC），在此声明
                requiredPlugins = emptySet(),
                capabilities = setOf(
                    BotCapability.GROUP_MESSAGE_SEND,
                    BotCapability.USER_MESSAGE_SEND,
                    BotCapability.IMAGE_SEND,
                    BotCapability.GROUP_MEMBER_QUERY,
                    BotCapability.GROUP_MEMBER_CHECK,
                    BotCapability.GROUP_MESSAGE_RECEIVE,
                    BotCapability.GROUP_MEMBER_DECREASE_RECEIVE
                )
            )

            override fun create(context: PlatformContext): IBot {
                return MyAdapter(context, metadata)
            }
        }
    """.trimIndent()

    val ADAPTER_TEMPLATE = """
        package com.example.plumbot.adapter.myadapter

        import me.regadpole.plumbot.bot.AbstractBotAdapter
        import me.regadpole.plumbot.bot.BotAdapterMetadata
        import me.regadpole.plumbot.bot.MemberInfo
        import me.regadpole.plumbot.platform.PlatformContext
        import java.util.concurrent.CompletableFuture

        class MyAdapter(
            override val context: PlatformContext,
            override val metadata: BotAdapterMetadata
        ) : AbstractBotAdapter() {

            override fun doStart() {
                // TODO: 建立连接、登录校验、注册监听器等
            }

            override fun doShutdown() {
                // TODO: 断开连接、清理资源
            }

            override fun preloadGroupCaches() {
                // TODO: 遍历 context.config.getLongList("groups") 预热缓存
            }

            override fun fetchMember(groupId: Long, userId: Long): CompletableFuture<MemberInfo?> {
                // TODO: 调用协议 SDK 查询群成员，异常时 completeExceptionally
                return CompletableFuture.completedFuture(null)
            }

            override fun loadGroupName(groupId: Long): CompletableFuture<String> {
                // TODO: 调用协议 SDK 查询群名称
                return CompletableFuture.completedFuture(groupId.toString())
            }

            override fun sendGroupMsg(targetId: Long, message: String) {
                // TODO: 调用协议 SDK 发送群文本消息
            }

            override fun sendUserMsg(targetId: Long, message: String) {
                // TODO: 调用协议 SDK 发送私聊文本消息
            }

            override fun sendGroupPicWithText(targetId: Long, message: String) {
                // TODO: 调用协议 SDK 发送群图片（或图文）消息
            }

            override fun sendUserPicWithText(targetId: Long, message: String) {
                // TODO: 调用协议 SDK 发送私聊图片（或图文）消息
            }
        }
    """.trimIndent()

    val LISTENER_TEMPLATE = """
        package com.example.plumbot.adapter.myadapter

        import me.regadpole.plumbot.task.TaskProviderImpl

        class MyAdapterListener(private val adapter: MyAdapter) {

            // 该方法名/注解仅为示意，请替换为实际协议 SDK 的事件订阅方式
            fun onGroupMessage(event: MyGroupMessageEvent) {
                val groups = adapter.context.config.getLongList("groups")
                if (!groups.contains(event.groupId)) return

                val message = buildString {
                    // TODO: 解析 event 中的文本/图片/At 等元素
                    append(event.rawMessage)
                }

                TaskProviderImpl.submitAsync {
                    adapter.handler?.onGroupMessage(message, event.groupId, event.senderId)
                }
            }

            fun onGroupMemberDecrease(event: MyGroupMemberDecreaseEvent) {
                val groups = adapter.context.config.getLongList("groups")
                if (!groups.contains(event.groupId)) return

                adapter.groupMemberCache.invalidate(event.groupId, event.userId)
                TaskProviderImpl.submitAsync {
                    adapter.handler?.onUserDecrease(event.groupId, event.userId)
                }
            }
        }
    """.trimIndent()

    val NOTES = """
        新增 Adapter 注意事项：
        1. 在 Factory 中正确声明 supportedPlatforms，否则 BotRegistry 不会将其注册到当前平台。
        2. 若协议依赖外部插件（如 MiraiMC），请在 requiredPlugins 中列出，PlumBot 会在启动前检查依赖。
        3. 根据实际能力声明 capabilities；上层命令会按 capability 校验功能是否可用。
        4. 所有阻塞式成员/群名查询请通过 AbstractBotAdapter.awaitWithTimeout 等待，保持默认超时一致。
        5. Listener 中收到事件后，统一调用 adapter.handler?.onGroupMessage/onUserDecrease 等 BotHandler 方法。
        6. 图片发送可复用 common 模块的 TextToImg.toImgCQCode / toFile 等工具，具体取决于协议 SDK 要求。
    """.trimIndent()
}
