package me.regadpole.plumbot.api

import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.exception.BotMessageSendException
import me.regadpole.plumbot.api.exception.BotNotReadyException
import me.regadpole.plumbot.bot.BotExtensionRegistry
import me.regadpole.plumbot.bot.MemberInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * PlumBot 面向第三方附属插件与扩展开发者提供的统一公开业务门面。
 *
 * 规范调用入口：
 * 1. 跨平台静态入口：[PlumBotAPI.get]
 * 2. Bukkit 服务管理器：`server.servicesManager.load(PlumBotAPI::class.java)`
 */
@StableApi
interface PlumBotAPI {

    // =========================================================================
    // 1. 消息发送与广播体系 (Dispatching & Broadcasting)
    // =========================================================================

    /**
     * 向指定群发送文本消息（支持可选的文本渲染图片模式）。
     *
     * @param groupId 目标群号
     * @param message 消息内容
     * @param asImage 是否以图片形式发送（默认 false）
     * @throws BotNotReadyException 当 Bot 未就绪时抛出
     * @throws BotMessageSendException 当底层网络协议发送失败时抛出
     */
    suspend fun sendGroupMessage(groupId: Long, message: String, asImage: Boolean = false)

    /**
     * 向指定群发送消息并 @指定成员（触发客户端强提醒）。
     *
     * @param groupId 目标群号
     * @param userId 目标 QQ 号
     * @param message 消息正文
     */
    suspend fun sendGroupMessageAt(groupId: Long, userId: Long, message: String)

    /**
     * 向指定群发送消息并 @全体成员。
     *
     * @param groupId 目标群号
     * @param message 消息正文
     */
    suspend fun sendGroupMessageAtAll(groupId: Long, message: String)

    /**
     * 直接向指定群发送本地图片文件（发原图，不经 AWT 文本绘制）。
     *
     * @param groupId 目标群号
     * @param imageFile 本地图片文件
     */
    suspend fun sendGroupImage(groupId: Long, imageFile: File)

    /**
     * 向当前所有配置互通的群一键广播消息。
     *
     * @param message 广播正文
     * @param asImage 是否以图片形式发送（默认 false）
     */
    suspend fun broadcastToAllGroups(message: String, asImage: Boolean = false)

    /**
     * 向指定用户发送私聊文本消息（支持可选的文本渲染图片模式）。
     *
     * @param userId 目标用户 QQ 号
     * @param message 消息正文
     * @param asImage 是否以图片形式发送（默认 false）
     */
    suspend fun sendUserMessage(userId: Long, message: String, asImage: Boolean = false)


    // =========================================================================
    // 2. 外部事件监听体系 (强制绑定宿主生命周期，杜绝 ClassLoader 内存泄漏)
    // =========================================================================

    /**
     * 订阅群消息接收事件（强制绑定调用方插件宿主）。
     * 当 [plugin] 被卸载或重载时，PlumBot 会自动切断该监听器，无需手动在 onDisable 注销。
     *
     * @param plugin 订阅方自身的插件实例
     * @param handler 事件监听回调
     * @return 用于主动注销的 [ListenerHandle]
     */
    fun subscribeGroupMessage(plugin: Plugin, handler: (GroupMessageEvent) -> Unit): ListenerHandle

    /**
     * 订阅群成员退群/减少事件（强制绑定调用方插件宿主）。
     *
     * @param plugin 订阅方自身的插件实例
     * @param handler 事件监听回调
     * @return 用于主动注销的 [ListenerHandle]
     */
    fun subscribeGroupMemberDecrease(plugin: Plugin, handler: (GroupMemberDecreaseEvent) -> Unit): ListenerHandle


    // =========================================================================
    // 3. 白名单与账号绑定体系 (Whitelist & Account Management) - 读写闭环
    // =========================================================================

    /**
     * 根据游戏内玩家名查询绑定的 QQ 账号。
     *
     * @param playerName 玩家游戏名（不区分大小写）
     * @return 绑定的 QQ 号，未绑定则为 null
     */
    suspend fun getBindingUser(playerName: String): Long?

    /**
     * 根据玩家 UUID 查询绑定的 QQ 账号。
     *
     * @param uuid 玩家 Mojang UUID
     * @return 绑定的 QQ 号，未绑定则为 null
     */
    suspend fun getBindingUser(uuid: UUID): Long?

    /**
     * 查询指定 QQ 用户绑定的所有游戏角色及对应的 UUID。
     * 直接从数据库记录映射直出，包含玩家名与其现成的 UUID，零多余网络开销。
     *
     * @param userId QQ 用户号
     * @return 玩家名与对应 UUID 的 Map（未记录 UUID 的角色其值为 null）
     */
    suspend fun getBindingAccounts(userId: Long): Map<String, UUID?>

    /**
     * 检查某个玩家名是否已经拥有白名单绑定记录。
     *
     * @param playerName 游戏名（不区分大小写）
     */
    suspend fun isPlayerWhitelisted(playerName: String): Boolean

    /**
     * 为指定 QQ 绑定新的白名单游戏角色（供商城发卡、答题进服插件调用）。
     *
     * @param userId QQ 用户号
     * @param playerName 游戏名（自动做格式合法性与重复性校验）
     * @param uuid 玩家 UUID（可选）
     * @return true 代表绑定成功，false 代表玩家名已存在或格式非法
     */
    suspend fun addBinding(userId: Long, playerName: String, uuid: UUID? = null): Boolean

    /**
     * 解绑并从白名单移出指定游戏角色（自动联动踢出该在线玩家）。
     *
     * @param playerName 要解绑的游戏名
     * @return true 代表成功解绑，false 代表该玩家原本未绑定
     */
    suspend fun removeBinding(playerName: String): Boolean


    // =========================================================================
    // 4. 群成员与权限鉴权体系 (Permissions & Profiles)
    // =========================================================================

    /**
     * 检查指定用户是否为 PlumBot 的机器人管理员（配置在 config.yml admins 清单中的 QQ）。
     * 外部管理插件可据此直接执行特权指令拦截。
     */
    fun isBotAdmin(userId: Long): Boolean

    /**
     * 获取指定群成员资料（群名片、昵称、群角色）。
     * 基于 aedile 协程缓存非阻塞高速读取。
     */
    suspend fun getGroupMemberInfo(groupId: Long, userId: Long): MemberInfo?

    /**
     * 检查用户在特定群内是否为群主或管理员（QQ 客户端群管权限）。
     */
    suspend fun isGroupAdmin(groupId: Long, userId: Long): Boolean


    // =========================================================================
    // 5. Bot 状态与元数据查询 (Metadata & Health)
    // =========================================================================

    /** 当前配置互通的所有群号集合 */
    val boundGroupIds: Set<Long>

    /** 当前运行的 Bot 实例类型标识或账号标识 */
    val currentBotId: String

    /** 当前 Bot 连接是否处于就绪状态 */
    val isBotConnected: Boolean

    /** 第三方注册自定义 BotAdapter 的扩展入口 */
    val extensionRegistry: BotExtensionRegistry


    // =========================================================================
    // 6. Java 异步互操作友好接口 (Java Future Overloads)
    // =========================================================================

    fun sendGroupMessageAsync(groupId: Long, message: String): CompletableFuture<Unit>
    fun broadcastToAllGroupsAsync(message: String): CompletableFuture<Unit>
    fun getBindingUserAsync(playerName: String): CompletableFuture<Long?>
    fun getBindingAccountsAsync(userId: Long): CompletableFuture<Map<String, UUID?>>
    fun isPlayerWhitelistedAsync(playerName: String): CompletableFuture<Boolean>

    companion object {
        /**
         * 获取当前已加载的全局 API 实例。
         *
         * @throws IllegalStateException 若 PlumBot 尚未完成加载
         */
        @JvmStatic
        fun get(): PlumBotAPI = me.regadpole.plumbot.internal.PlumBotApiProvider.requireInstance()
    }
}
