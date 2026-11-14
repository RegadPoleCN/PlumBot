// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.api.bot.BotCapability
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 能力缺失时的降级语义：绑定回复与群转发的**主功能不能因为能力未声明而整条静默丢失**。
 *
 * 此前两条路径都在能力缺失时抛 IllegalStateException 且没有日志：不支持发图的适配器上
 * 所有绑定/白名单回复（含出错兜底提示）被吞掉，to_server 开着也一条都转不过去。
 */
class CapabilityDegradeTest {

    private val groupId = 10000L
    private val userId = 20000L

    // 单测不加载 messages.yml（模板是 <missing:key>），临时注入真模板以断言占位文本
    private val ob2serverBackup = Messages.ob2server

    @AfterTest
    fun restoreMessages() {
        Messages.ob2server = ob2serverBackup
    }

    @Test
    fun `bind replies degrade to plain text when IMAGE_SEND is missing`() {
        val ctx = FakePlatformContext()
        val bot = FakeBot(capabilities = setOf(BotCapability.GROUP_MESSAGE_RECEIVE, BotCapability.GROUP_MESSAGE_SEND))
        val service = BotCommandService(ctx, bot)

        service.sendBindMessage(groupId, "绑定提示正文")
        service.sendBindMessage(groupId, "第二条提示")

        // 两条回复都必须送达（纯文本），而不是被能力门槛整条吞掉
        assertEquals(listOf("绑定提示正文", "第二条提示"), bot.sentMessages)
        // 降级只告警一次，避免每条回复刷屏
        assertEquals(1, ctx.logs.count { "IMAGE_SEND" in it }, "缺少 IMAGE_SEND 的降级告警只应出现一次")
    }

    @Test
    fun `ob2server forward degrades when GROUP_MEMBER_QUERY is missing`() {
        val ctx = FakePlatformContext(
            mapOf(
                "feature.message.enable" to true,
                "feature.message.to_server" to true,
                "feature.message.mode" to 0,
                "feature.message.prefix" to "#",
            )
        )
        val bot = FakeBot(capabilities = setOf(BotCapability.GROUP_MESSAGE_RECEIVE, BotCapability.GROUP_MESSAGE_SEND))
        val service = BotCommandService(ctx, bot)
        val forwarder = MessageForwardService(service)
        Messages.ob2server = "[%group_name%] <%user_name%>: %message%"

        forwarder.forward("hello", groupId, userId)
        forwarder.forward("second", groupId, userId)

        // 转发主功能必须保住：占位文本替代群名/昵称，正文照转
        assertEquals(2, ctx.sentMessages.size, "能力缺失时转发不得整条静默丢失")
        assertTrue(ctx.sentMessages[0].contains("未知群组") && ctx.sentMessages[0].contains("hello"), "首条转发应带占位文本与正文: " + ctx.sentMessages[0])
        assertTrue(ctx.sentMessages[1].contains("second"), "第二条照常转发")
        assertEquals(1, ctx.logs.count { "GROUP_MEMBER_QUERY" in it }, "缺少 GROUP_MEMBER_QUERY 的降级告警只应出现一次")
    }

    @Test
    fun `bind replies keep image path when IMAGE_SEND is declared`() {
        val ctx = FakePlatformContext()
        val bot = FakeBot(capabilities = setOf(BotCapability.GROUP_MESSAGE_RECEIVE, BotCapability.GROUP_MESSAGE_SEND, BotCapability.IMAGE_SEND))
        val service = BotCommandService(ctx, bot)

        // 有门户时走渲染路径；测试环境无门户 → 降级纯文本。此处只验证不抛异常且不产生降级告警
        service.sendBindMessage(groupId, "normal")

        assertTrue(bot.sentMessages.isNotEmpty(), "能力齐备时回复照常送达")
        assertTrue(ctx.logs.none { "IMAGE_SEND" in it }, "声明了 IMAGE_SEND 就不该出现降级告警")
    }
}
