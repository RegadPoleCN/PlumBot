// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **"配置关闭"与"渲染降级"必须分开计数**：两者在用户侧都表现为"消息变成纯文本"，
 * 但前者是预期、后者是故障；混用同一个计数器会让降级指标长期虚高，真故障被淹没（公告每次启停 +1）。
 */
class RenderDowngradeAccountingTest {

    @Test
    fun `没有渲染门户时图片请求计为配置关闭并降级为纯文本`() {
        RenderProvider.shutdown()
        HealthCounters.reset()
        val bot = FakeBot(FakePlatformContext())
        bot.start()
        try {
            bot.sendGroupPicWithText(42L, "渲染内容")
            assertEquals(
                1L,
                HealthCounters.get(HealthCounters.Key.RENDER_DISABLED),
                "没有门户属于能力/配置关闭，不应计入渲染降级",
            )
            assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK), "配置关闭不应污染降级计数")
            assertEquals(listOf("渲染内容"), bot.sentMessages, "仍须把纯文本发出去")
        } finally {
            bot.shutdown()
        }
    }

    @Test
    fun `引擎配置为 none 时图片请求计为配置关闭`() {
        HealthCounters.reset()
        val ctx = FakePlatformContext(mapOf("render.engine" to "none"))
        RenderProvider.bootstrap(ctx)
        val bot = FakeBot(ctx)
        bot.start()
        try {
            bot.sendGroupPicWithText(7L, "无图")
            assertEquals(1L, HealthCounters.get(HealthCounters.Key.RENDER_DISABLED), "engine=none 是主动关闭")
            assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK))
            assertEquals(listOf("无图"), bot.sentMessages)
            assertTrue(
                ctx.logs.any { it.contains("未启用图片") },
                "配置关闭只留 DEBUG 线索，不能刷 WARN；实际日志: ${ctx.logs}",
            )
        } finally {
            bot.shutdown()
            RenderProvider.shutdown()
        }
    }
}
