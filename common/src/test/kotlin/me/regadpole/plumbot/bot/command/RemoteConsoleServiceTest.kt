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

package me.regadpole.plumbot.bot.command

import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * R05 端到端：远程控制台的**闸门、输出归一化与截断联动**。
 *
 * 覆盖点（前三点此前完全无测试）：
 * 1. 非管理员被拦截（且不触碰平台调度）；
 * 2. 超频被限流（按 QQ）；
 * 3. 控制序列被清理后才进聊天（否则聊天里是乱码，且会吃掉截断额度）；
 * 4. `max_output_length` 超过平台捕获上限时**钳制并显式告知**（不再是静默不一致）。
 */
class RemoteConsoleServiceTest {

    private val adminQq = 10001L
    private val groupId = 20002L

    private fun context(extra: Map<String, Any> = emptyMap()): FakePlatformContext =
        FakePlatformContext(
            mapOf(
                "feature.remote_console.enable" to true,
                "feature.remote_console.log_commands" to true,
                "feature.remote_console.max_output_length" to 1500,
                "feature.remote_console.rate_limit_per_minute" to 10,
                "feature.remote_console.max_concurrent" to 4,
                "admins" to listOf(adminQq),
            ) + extra
        )

    private fun service(context: FakePlatformContext): Pair<RemoteConsoleCommandService, FakeBot> {
        val bot = FakeBot(context)
        val commandService = BotCommandService(context, bot)
        return RemoteConsoleCommandService(commandService) to bot
    }

    /** 服务最终把回显交给 bot 或 platform 之一（两条通道都收集，断言更稳）。 */
    private fun allOutput(context: FakePlatformContext, bot: FakeBot): String =
        (context.sentMessages + bot.sentMessages + context.logs).joinToString("\n")

    @Test
    fun `non admins are blocked before the platform is touched`() {
        val context = context()
        val (service, bot) = service(context)

        service.execute("say hi", groupId, userId = 999L)

        assertTrue(context.consoleCommands.isEmpty(), "非管理员不得触发平台执行")
        assertTrue(allOutput(context, bot).isNotEmpty(), "应给出明确拒绝提示")
    }

    @Test
    fun `executed output is sanitized before reaching the chat`() {
        val context = context()
        context.consoleCommandResult = { CompletableFuture.completedFuture("\u001B[32m[Server] 完成\u001B[0m\u001B[K") }
        val (service, bot) = service(context)

        service.execute("list", groupId, adminQq)

        assertEquals(listOf("list"), context.consoleCommands, "指令应原样交给平台（去掉前导 /）")
        val output = allOutput(context, bot)
        assertFalse(output.contains('\u001B'), "控制序列必须在进聊天前被清理：$output")
        assertTrue(output.isNotBlank(), "执行后必须给出回显：$output")
        // 说明：本项目单测**不加载 messages.yml**（见 `BindApplyCommandGroupGateTest` / `CommandGateForwardingTest` 的同类注释），
        // 因此 `Messages.remoteCommandExecuted` 会渲染成 `<missing:…>`，正文内容在此不可观测。
        // "内容本身被保留（只去掉控制序列）"由 `ConsoleTextTest` 直接覆盖。
    }

    @Test
    fun `the same qq is rate limited`() {
        val context = context(mapOf("feature.remote_console.rate_limit_per_minute" to 1))
        val (service, bot) = service(context)

        service.execute("list", groupId, adminQq)
        service.execute("list", groupId, adminQq)

        assertEquals(1, context.consoleCommands.size, "第二次必须被限流，不得执行")
        assertTrue(allOutput(context, bot).contains("频繁"), "限流应给出可理解的提示：${allOutput(context, bot)}")
    }

    @Test
    fun `a limit above the platform ceiling is clamped and reported`() {
        val context = context(mapOf("feature.remote_console.max_output_length" to 99_999))
        context.consoleCommandResult = { CompletableFuture.completedFuture("x".repeat(3_000)) }
        val (service, bot) = service(context)

        service.execute("list", groupId, adminQq)

        val output = allOutput(context, bot)
        assertTrue(output.contains("超过平台捕获上限"), "配置超上限必须显式告知，而不是静默截断：$output")
        assertTrue(
            output.length < 3_000,
            "回显长度应被钳制到平台上限附近（实际 ${output.length}）—— 若等于原始长度说明截断根本没生效"
        )
    }
}
