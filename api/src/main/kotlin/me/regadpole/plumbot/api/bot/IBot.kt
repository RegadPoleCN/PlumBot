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

    /** 适配器的类型、显示名与能力声明。 */
    val metadata: BotAdapterMetadata

    /**
     * 当前连接状态。
     *
     * **默认返回 [BotState.INIT]**：`IBot` 是 `@StableApi`，给默认值可让既有第三方适配器
     * 不改一行也能编译；实现了状态迁移的适配器应覆盖它。调用方据此判断"现在能不能发消息"，
     * 布尔值无法区分"在线"与"正在重连"。
     */
    val state: BotState get() = BotState.INIT

    /** 是否**可用**（等价于 `state == BotState.ONLINE`）；默认实现基于 [state]。 */
    fun isOnline(): Boolean = state.isUsable

    fun start(): IBot

    fun shutdown()

    /** 查询群名称；查询失败或超时时返回群号。 */
    fun getGroupName(groupId: Long): String

    /**
     * 判断用户是否在群内；查询失败时按"不在群"处理。
     */
    fun checkUserInGroup(userId: Long, groupId: Long): Boolean

    /**
     * 使指定成员在**本地成员缓存**中失效（退群/被踢）。
     *
     * 成员缓存默认 30 分钟过期；不主动失效的话，已退群者会在 TTL 内仍被判定为"在群内"，
     * 影响显示名与绑定校验。默认实现**什么都不做**：给默认实现可避免第三方适配器升级时编译失败，
     * 没有本地缓存的适配器本来就无需实现。
     */
    fun invalidateGroupMember(groupId: Long, userId: Long) {}

    /**
     * 发送消息；[isPic] 为 `true` 时按图片+文字发送，否则发纯文本。
     *
     * 图片分支委托给 [sendGroupPicWithText] / [sendUserPicWithText]，
     * 以保持旧 `sendPictureWithText` 的行为不变。
     */
    fun sendMsg(isGroup: Boolean, targetId: Long, message: String?, isPic: Boolean)

    fun sendGroupMsg(targetId: Long, message: String)

    fun sendUserMsg(targetId: Long, message: String)

    fun sendGroupPicWithText(targetId: Long, message: String)

    fun sendUserPicWithText(targetId: Long, message: String)

    /** 群成员昵称；缓存未命中或查询失败时回退为 QQ 号。 */
    fun getGroupUserName(groupId: Long, targetId: Long): String

    /** 群名片；未设置时回退昵称，再回退为 QQ 号。 */
    fun getGroupUserCard(groupId: Long, targetId: Long): String

    /**
     * 直发本地图片文件到群，不做文本绘制。
     *
     * **返回值契约（失败可见化）**：`true` 表示图片已交给协议层发出；`false` 表示
     * **明确的发送失败**（连接未就绪、上传被拒、平台不支持等）。实现方在无法确认结果时
     * 应返回 `true`（与旧 void 语义等价）；通道异常仍可用异常表达。
     * 调用方（`AbstractBotAdapter.dispatchRoute`）据此决定是否回落纯文本。
     */
    fun sendGroupImage(targetId: Long, imageFile: java.io.File): Boolean

    /** [sendGroupImage] 的字节数组重载。 */
    fun sendGroupImage(targetId: Long, imageBytes: ByteArray): Boolean {
        val temp = java.io.File.createTempFile("plumbot_img_", ".png")
        try {
            temp.writeBytes(imageBytes)
            return sendGroupImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    /**
     * 直发本地图片文件到私聊，与 [sendGroupImage] 对称。
     *
     * 默认实现**立即报错而不是留空**：`IBot` 是 `@StableApi`，新增**抽象**成员会让所有
     * 第三方实现在升级时直接编译失败；给默认实现则把"不支持私聊图片"从编译期错误
     * 变为运行期显式失败 —— 既不静默丢图，也保住了源码与二进制兼容。
     *
     * 注意：四个发图方法的返回类型由 `Unit` 改为 `Boolean`（失败可见化）。这对**重写**了
     * 这些方法的第三方适配器是源码不兼容变更（override 需补 `: Boolean`），未重写者不受影响。
     */
    fun sendUserImage(targetId: Long, imageFile: java.io.File): Boolean {
        throw UnsupportedOperationException(
            "该 Bot 适配器未实现私聊图片直发（sendUserImage）；" +
                "请在适配器中实现该方法，或检查 metadata.capabilities 是否应声明 IMAGE_SEND。"
        )
    }

    /** [sendUserImage] 的字节数组重载。 */
    fun sendUserImage(targetId: Long, imageBytes: ByteArray): Boolean {
        val temp = java.io.File.createTempFile("plumbot_img_", ".png")
        try {
            temp.writeBytes(imageBytes)
            return sendUserImage(targetId, temp)
        } finally {
            runCatching { temp.delete() }
        }
    }

    fun sendGroupMsgAt(targetId: Long, userId: Long, message: String)

    fun sendGroupMsgAtAll(targetId: Long, message: String)
}