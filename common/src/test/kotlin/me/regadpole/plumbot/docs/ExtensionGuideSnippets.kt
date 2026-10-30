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

package me.regadpole.plumbot.docs

import me.regadpole.plumbot.api.ListenerHandle
import me.regadpole.plumbot.api.PlumBotAPI
import me.regadpole.plumbot.api.PlumBotApiProvider
import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.api.platform.PlatformTaskHandle
import me.regadpole.plumbot.bot.BotEventDispatcher
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 扩展开发手册（`coding-docs/extension-guide.md`）中示例的**可编译版本**：文档不会被编译，
 * 示例与源码脱节时错误长期无人发现；固化成本文件后，任何 API 改名/改签名都会让
 * `:common:compileTestKotlin` 直接失败，文档与代码不可能再各说各话。
 *
 * 维护约定：改**文档示例**时同步改这里；只引用公开契约（`:api` 与 `:common` 的 @PublicApi 面）。
 */
@Suppress("unused")
object ExtensionGuideSnippets {

    /** ① 可空获取 API 实例。 */
    fun apiOrNull(): PlumBotAPI? = PlumBotApiProvider.getInstanceOrNull()

    /** ② 能力位名称。 */
    fun declaredCapabilities(): Set<BotCapability> = setOf(
        BotCapability.GROUP_MESSAGE_SEND,
        BotCapability.IMAGE_SEND,
        BotCapability.GROUP_MESSAGE_RECEIVE,
        BotCapability.GROUP_MEMBER_QUERY,
    )

    /** ③ 绑定查询是 **suspend** 函数。 */
    suspend fun bindingOf(api: PlumBotAPI, playerName: String): Long? = api.getBindingUser(playerName)

    suspend fun bindingOf(api: PlumBotAPI, uuid: UUID): Long? = api.getBindingUser(uuid)

    suspend fun accountsOf(api: PlumBotAPI, qq: Long): Map<String, UUID?> = api.getBindingAccounts(qq)

    /** ④ 事件订阅：返回句柄，用 `close()` 注销。 */
    fun subscribeGroupMessages(): ListenerHandle =
        BotEventDispatcher.registerGroupMessageHandler { event ->
            event.groupId
            event.userId
            event.message
        }

    /** ⑤ 调度：平台契约是 `runAsync` / `runLater(Duration)`。 */
    fun runAsync(context: PlatformContext, body: () -> Unit): PlatformTaskHandle =
        context.scheduler.runAsync(body)

    /** `Duration` 是 **`kotlin.time.Duration`**（`2.seconds`），不是 `java.time.Duration` —— 传错编译期即失败。 */
    fun runLater(context: PlatformContext, body: () -> Unit): PlatformTaskHandle =
        context.scheduler.runLater(2.seconds, body)
}
