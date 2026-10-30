// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.api

import me.regadpole.plumbot.api.event.GroupMemberDecreaseEvent
import me.regadpole.plumbot.api.event.GroupMessageEvent
import me.regadpole.plumbot.api.exception.BotMessageSendException
import me.regadpole.plumbot.api.exception.BotNotReadyException
import me.regadpole.plumbot.api.bot.BotExtensionRegistry
import me.regadpole.plumbot.api.bot.MemberInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * PlumBot 面向第三方附属插件与扩展开发者的统一公开业务门面。
 * 入口：[PlumBotAPI.get]；Bukkit 侧亦可用 `server.servicesManager.load(PlumBotAPI::class.java)`。
 *
 * 所有 `suspend` 方法内部切到 `Dispatchers.IO`，可在任意线程（含主线程）调用。
 */
@StableApi
interface PlumBotAPI {

    // 1. 消息发送与广播

    /**
     * 向指定群发送文本消息；`asImage = true` 时渲染为图片后发送。
     * @throws BotNotReadyException Bot 未就绪
     * @throws BotMessageSendException 协议层发送失败
     */
    suspend fun sendGroupMessage(groupId: Long, message: String, asImage: Boolean = false)

    /**
     * 向指定群发送消息并 @指定成员（触发客户端强提醒），[userId] 为被 @ 的 QQ 号。
     */
    suspend fun sendGroupMessageAt(groupId: Long, userId: Long, message: String)

    /** 向指定群发送消息并 @全体成员。 */
    suspend fun sendGroupMessageAtAll(groupId: Long, message: String)

    /** 直接向指定群发本地图片文件（原图，不经 AWT 绘制）；文件不存在抛 IllegalArgumentException。 */
    suspend fun sendGroupImage(groupId: Long, imageFile: File)

    /** 直接向指定群发内存图片字节（原图，不经文本绘制），省去落盘；字节为空抛 IllegalArgumentException。 */
    suspend fun sendGroupImage(groupId: Long, imageBytes: ByteArray)

    /** 向当前所有配置互通的群广播消息；单个群失败只记日志，不中断其余群、不抛异常。 */
    suspend fun broadcastToAllGroups(message: String, asImage: Boolean = false)

    /** 向指定用户发送私聊消息；`asImage = true` 时以渲染图发送。 */
    suspend fun sendUserMessage(userId: Long, message: String, asImage: Boolean = false)


    // 2. 外部事件监听（强制绑定宿主生命周期，杜绝 ClassLoader 泄漏）

    /**
     * 订阅群消息接收事件：强制绑定 [plugin] 宿主，插件卸载/重载时自动切断，无需手动注销；
     * 返回的 [ListenerHandle.close] 幂等。
     */
    fun subscribeGroupMessage(plugin: Plugin, handler: (GroupMessageEvent) -> Unit): ListenerHandle

    /** 订阅群成员退群/减少事件；宿主绑定与注销契约同 [subscribeGroupMessage]。 */
    fun subscribeGroupMemberDecrease(plugin: Plugin, handler: (GroupMemberDecreaseEvent) -> Unit): ListenerHandle


    // 3. 白名单与账号绑定

    /** 按游戏内玩家名查绑定的 QQ 号，未绑定返回 null（玩家名不区分大小写）。 */
    suspend fun getBindingUser(playerName: String): Long?

    /** 按玩家 UUID 查绑定的 QQ 号，未绑定返回 null。 */
    suspend fun getBindingUser(uuid: UUID): Long?

    /**
     * 查询指定 QQ 绑定过的所有游戏角色及其 UUID；直接读库映射，无网络开销。
     * @return 玩家名 → UUID（未记录 UUID 的角色其值为 null）
     */
    suspend fun getBindingAccounts(userId: Long): Map<String, UUID?>

    /** 该玩家名是否已有白名单绑定记录（不区分大小写）。 */
    suspend fun isPlayerWhitelisted(playerName: String): Boolean

    /**
     * 为指定 QQ 绑定新的白名单角色（供商城发卡、答题进服插件调用）。
     * @return true 成功；false 表示格式非法、绑定已存在或写库失败
     */
    suspend fun addBinding(userId: Long, playerName: String, uuid: UUID? = null): Boolean

    /**
     * 解绑并从白名单移出（自动踢出在线玩家），玩家名按小写归一化匹配。
     * @return false 表示该玩家原本未绑定
     */
    suspend fun removeBinding(playerName: String): Boolean


    // 4. 群成员与权限鉴权

    /** 是否为机器人管理员（config.yml 的 `admins` 清单；同步读配置，不阻塞）。 */
    fun isBotAdmin(userId: Long): Boolean

    /**
     * 取群成员资料（群名片、昵称、群角色），基于 aedile 缓存非阻塞读取。
     * 未知成员或 Bot 非 AbstractBotAdapter 时返回 null。
     */
    suspend fun getGroupMemberInfo(groupId: Long, userId: Long): MemberInfo?

    /** 用户在特定群内是否为群主或管理员（QQ 客户端群管权限，非机器人管理员）。 */
    suspend fun isGroupAdmin(groupId: Long, userId: Long): Boolean


    // 5. Bot 状态与元数据

    /** 当前配置互通的所有群号集合 */
    val boundGroupIds: Set<Long>

    /** 当前运行的 Bot 实例类型标识或账号标识 */
    val currentBotId: String

    /**
     * 当前 Bot 是否处于 `BotState.ONLINE`（CONNECTING/RETRYING 等一律为 false）。
     * @deprecated 语义含糊（"已连接"与"已加载"未区分）；主版本内保留。
     */
    @Deprecated("语义含糊，请改用状态查询 API")
    val isBotConnected: Boolean

    /** 第三方注册自定义 BotAdapter 的扩展入口 */
    val extensionRegistry: BotExtensionRegistry


    // 6. Java 异步互操作：与同名 suspend 方法语义一致，异常以 CompletionException 结束 Future

    fun sendGroupMessageAsync(groupId: Long, message: String): CompletableFuture<Unit>
    fun broadcastToAllGroupsAsync(message: String): CompletableFuture<Unit>
    fun getBindingUserAsync(playerName: String): CompletableFuture<Long?>
    fun getBindingAccountsAsync(userId: Long): CompletableFuture<Map<String, UUID?>>
    fun isPlayerWhitelistedAsync(playerName: String): CompletableFuture<Boolean>

    companion object {
        /**
         * 获取当前已加载的全局 API 实例；未加载或已卸载时抛 IllegalStateException。
         */
        @JvmStatic
        fun get(): PlumBotAPI = PlumBotApiProvider.get()
    }
}
