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

package me.regadpole.plumbot.api.bot

import me.regadpole.plumbot.api.StableApi

@StableApi
interface IBot {
    var handler: BotHandler?

    /**
     * Metadata of the adapter that created this bot.
     */
    val metadata: BotAdapterMetadata

    /**
     * 当前连接状态（R02）。
     *
     * **默认实现返回 [BotState.INIT]**：`IBot` 是 `@StableApi`，给默认值可让既有第三方适配器
     * 不改一行也能编译；实现了状态迁移的适配器（如内置三端）会覆盖它并给出真实状态。
     *
     * 使用者应据此判断"现在到底能不能发消息" —— 旧的布尔值无法区分"在线"与"正在重连"。
     */
    val state: BotState get() = BotState.INIT

    /** 是否**可用**（等价于 `state == BotState.ONLINE`）；默认实现基于 [state]。 */
    fun isOnline(): Boolean = state.isUsable

    /**
     * Start a bot
     */
    fun start(): IBot

    /**
     * Stop a bot
     */
    fun shutdown()

    /**
     * Get the group name
     * @param groupId the id of group
     * @return the group name
     */
    fun getGroupName(groupId: Long): String

    /**
     * check if the user is in group
     * @param userId the id of user
     * @param groupId the id of group
     * @return true if the user is in group, false otherwise
     */
    fun checkUserInGroup(userId: Long, groupId: Long): Boolean

    /**
     * 使指定成员在本适配器的**本地成员缓存**中失效。
     *
     * 用于"成员关系已改变"的场景（退群/被踢）。适配器的成员信息带有写入过期
     * （`GroupMemberCache` 默认 30 分钟），若不主动失效，已退群者会在缓存 TTL 内
     * 继续被判定为"仍在群内"—— 这会影响显示名、以及绑定时的成员校验。
     *
     * 默认实现**什么都不做**：`IBot` 是 `@StableApi`，给出默认实现可避免第三方适配器
     * 在升级时编译失败；没有本地缓存的适配器本来就无需实现。
     */
    fun invalidateGroupMember(groupId: Long, userId: Long) {}

    /**
     * Send message to the group/user.
     *
     * When [isPic] is `true`, this internally delegates to
     * [sendGroupPicWithText] (for [isGroup] = `true`) or
     * [sendUserPicWithText] (for [isGroup] = `false`), preserving the
     * exact behavior of the previously public `sendPictureWithText`.
     *
     * @param isGroup is this message send to group
     * @param targetId the target you want to send
     * @param message the message
     * @param isPic when `true`, send as a picture with text via
     *   [sendGroupPicWithText] / [sendUserPicWithText]; otherwise send as
     *   a plain text message via [sendGroupMsg] / [sendUserMsg]
     */
    fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean)

    /**
     * Send a group message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupMsg(targetId: Long, message: String)

    /**
     * Send a user message
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserMsg(targetId: Long, message: String)

    /**
     * Send a picture to group
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendGroupPicWithText(targetId: Long, message: String)

    /**
     * Send a picture to user
     * @param targetId ID of the target
     * @param message Message to send
     */
    fun sendUserPicWithText(targetId: Long, message: String)

    /**
     * Get user's name from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the name of user
     */
    fun getGroupUserName(groupId: Long, targetId: Long): String

    /**
     * Get user's card from group list
     * @param groupId group's id
     * @param targetId user's id
     * @return the card of user in group
     */
    fun getGroupUserCard(groupId: Long, targetId: Long): String

    /**
     * Send a local image file directly to the group without text-to-image conversion.
     */
    fun sendGroupImage(targetId: Long, imageFile: java.io.File)

    /**
     * Send an in-memory image byte array directly to the group.
     */
    fun sendGroupImage(targetId: Long, imageBytes: ByteArray) {
        val temp = java.io.File.createTempFile("plumbot_img_", ".png")
        try {
            temp.writeBytes(imageBytes)
            sendGroupImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    /**
     * Send a local image file directly to a private user (原图直发，不经文本绘制)。
     *
     * 与 [sendGroupImage] 对称：渲染链路产出的是字节，群与私聊都需要直发通道。
     *
     * 默认实现**立即报错而不是留空**：`IBot` 是 `@StableApi`，新增**抽象**成员会让所有
     * 第三方实现在升级时直接编译失败；给默认实现则把"该适配器不支持私聊图片"从编译期错误
     * 变为运行期显式失败 —— 既不静默丢图，也保住了源码与二进制兼容。
     */
    fun sendUserImage(targetId: Long, imageFile: java.io.File) {
        throw UnsupportedOperationException(
            "该 Bot 适配器未实现私聊图片直发（sendUserImage）；" +
                "请在适配器中实现该方法，或检查 metadata.capabilities 是否应声明 IMAGE_SEND。"
        )
    }

    /**
     * Send an in-memory image byte array directly to a private user.
     */
    fun sendUserImage(targetId: Long, imageBytes: ByteArray) {
        val temp = java.io.File.createTempFile("plumbot_img_", ".png")
        try {
            temp.writeBytes(imageBytes)
            sendUserImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    /**
     * Send group message and @ the target user.
     */
    fun sendGroupMsgAt(targetId: Long, userId: Long, message: String)

    /**
     * Send group message and @ all members.
     */
    fun sendGroupMsgAtAll(targetId: Long, message: String)
}