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

package me.regadpole.plumbot.bot

import me.regadpole.plumbot.internal.HealthCounters
import me.regadpole.plumbot.render.RenderProvider
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 回归：**"渲染成功、发图失败"必须可见地降级为纯文本**。
 *
 * 四个发图方法改为返回 `Boolean` 是为了让"适配器吞掉失败"（MiraiMC 的 `runCatching`、
 * OneBot 的异步重连）不再表现为静默失败。本用例钉住：门户确实产出了 PNG、而
 * `sendGroupImage` 报告失败时，群必须收到纯文本回退、`RENDER_SEND_FAILED` 恰好 +1、
 * 且只留一条 WARN。
 *
 * 用真实门户而不是桩：`RenderProvider.portal` 是私有字段，无 setter 也无注入点
 * （`registerEngineForTest` 在 `:render` 内为 `internal`，`:common` 不可见），只能经
 * `bootstrap` 装配。文本路由 `TEXT` 的首选引擎为 `null` → 兜底 AWT，无头 JVM 可稳定出图。
 * 反射改私有字段会引入仓库中无先例的 hack，字段改名还会以 `NoSuchFieldException` 误报。
 */
class RenderSendFailureFallbackTest {

    @Test
    fun `发图返回 false 时回落纯文本且只计一次发送失败`() = withRenderedBot(SendMode.RETURN_FALSE) { bot, ctx ->
        bot.sendGroupPicWithText(42L, "渲染内容")

        assertTrue(
            awaitTimeout { bot.sentMessages.isNotEmpty() },
            "出图任务必须在超时前走完（回退文本是最后一步）；实际日志: ${ctx.logs}",
        )
        assertEquals(
            1,
            bot.imageAttempts(),
            "必须真的尝试过一次发图——否则断言的是渲染失败路径（若本机缺 AWT 图形环境，门户会提前返回 Failed）",
        )
        val png = bot.sentImages.single()
        assertTrue(png.size > 8, "门户应产出真实 PNG 字节，实际 ${png.size} 字节")
        assertEquals(0x89.toByte(), png[0], "PNG 签名首字节")

        assertEquals(listOf("渲染内容"), bot.sentMessages, "发图失败必须把降级纯文本发出去，且只发一次")
        assertEquals(1L, HealthCounters.get(HealthCounters.Key.RENDER_SEND_FAILED), "发送失败必须单独计数")
        assertEquals(
            0L,
            HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK),
            "图是好的：发送失败不能混进渲染降级计数",
        )
        assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_DISABLED), "配置是开的，不算主动关闭")
        assertEquals(1, countSendFailedWarns(ctx), "一次失败只能留一条 WARN，实际日志: ${ctx.logs}")
    }

    @Test
    fun `发图抛异常时同样回落纯文本并带出异常类型`() = withRenderedBot(SendMode.THROW) { bot, ctx ->
        bot.sendGroupPicWithText(7L, "渲染内容")

        assertTrue(
            awaitTimeout { bot.sentMessages.isNotEmpty() },
            "出图任务必须在超时前走完；实际日志: ${ctx.logs}",
        )
        assertEquals(1, bot.imageAttempts())
        assertEquals(listOf("渲染内容"), bot.sentMessages, "异常同样要回退为纯文本，不能静默丢图")
        assertEquals(1L, HealthCounters.get(HealthCounters.Key.RENDER_SEND_FAILED))
        assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_FALLBACK))
        assertEquals(1, countSendFailedWarns(ctx))
        assertTrue(
            sendFailedWarns(ctx).any { it.contains("IllegalStateException") },
            "WARN 必须带出失败原因（异常类型），否则排查时只能看到“发图失败”四个字；实际日志: ${ctx.logs}",
        )
    }

    @Test
    fun `发图成功时不回落纯文本也不计发送失败`() = withRenderedBot(SendMode.RETURN_TRUE) { bot, ctx ->
        bot.sendGroupPicWithText(9L, "渲染内容")

        assertTrue(
            awaitTimeout { bot.imageAttempts() >= 1 },
            "出图任务必须在超时前走完；实际日志: ${ctx.logs}",
        )
        // 成功路径在调用`sendGroupImage`之后没有任何后续动作，留一点时间让协程收尾，
        // 避免"回退文本还没发出就先断言为空"的假通过。
        Thread.sleep(200L)

        assertEquals(1, bot.imageAttempts())
        assertTrue(bot.sentMessages.isEmpty(), "图已送达时不应额外发一条重复的纯文本")
        assertEquals(0L, HealthCounters.get(HealthCounters.Key.RENDER_SEND_FAILED), "成功发送不得计数")
        assertEquals(0, countSendFailedWarns(ctx))
    }

    /** 发图行为：按需模拟"返回 false"（MiraiMC/OneBot 的失败可见化）与"抛异常"（通道中断）。 */
    private enum class SendMode { RETURN_FALSE, THROW, RETURN_TRUE }

    /** 继承 [FakeBot] 而非直接实现 [me.regadpole.plumbot.api.bot.IBot]：派发、降级与计数都在 [AbstractBotAdapter] 里。 */
    private class SendFailingBot(
        context: FakePlatformContext,
        private val mode: SendMode,
    ) : FakeBot(context) {

        /** 记录门户产出的字节：字节非空即证明"渲染成功"，这是发送失败路径的前提。 */
        val sentImages = CopyOnWriteArrayList<ByteArray>()

        private val attempts = AtomicInteger(0)

        fun imageAttempts(): Int = attempts.get()

        override fun sendGroupImage(targetId: Long, imageBytes: ByteArray): Boolean {
            attempts.incrementAndGet()
            sentImages += imageBytes
            return when (mode) {
                SendMode.RETURN_FALSE -> false
                SendMode.THROW -> throw IllegalStateException("测试：发送通道已断开")
                SendMode.RETURN_TRUE -> true
            }
        }
    }

    /**
     * 装配"真实门户 + 会失败的发图适配器"，结束时还原全局状态。
     *
     * `render.outputs.text=true` 是必须的：[me.regadpole.plumbot.testing.FakePlatformConfig]
     * 对缺失键返回 `false`，不显式打开会落进"配置关闭"分支，走不到发图。
     */
    private fun <T> withRenderedBot(mode: SendMode, block: (SendFailingBot, FakePlatformContext) -> T): T {
        RenderProvider.shutdown()
        HealthCounters.reset()
        val ctx = FakePlatformContext(mapOf("render.outputs.text" to true))
        RenderProvider.bootstrap(ctx)
        val bot = SendFailingBot(ctx, mode)
        bot.start()
        try {
            return block(bot, ctx)
        } finally {
            bot.shutdown()
            RenderProvider.shutdown()
            HealthCounters.reset()
        }
    }

    /** 有界等待：出图在 IO 作用域异步执行，用轮询把"时序"从断言里摘出去。 */
    private fun awaitTimeout(timeoutMs: Long = 5_000L, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20L)
        }
        return condition()
    }

    /** "发送失败"类 WARN（基类 [AbstractBotAdapter] 在失败时写的那一条）。 */
    private fun sendFailedWarns(ctx: FakePlatformContext): List<String> =
        ctx.logs.filter { it.startsWith("WARN") && it.contains("图片未送达") }

    private fun countSendFailedWarns(ctx: FakePlatformContext): Int = sendFailedWarns(ctx).size
}
