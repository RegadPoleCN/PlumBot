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

package me.regadpole.plumbot.command

import me.regadpole.plumbot.utils.toPlainText
import net.kyori.adventure.text.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `/plumbot` 三端统一实现的语义测试。
 *
 * 各端自写一份 help/status/bind 时文案与边界会漂移（例如 Hytale 的 `bind add` 参数校验
 * 顺序与另外两端不同）。公共语义钉死在 common，本类就是那份契约。
 */
class PlumBotCommandHandlerTest {

    private class RecordingOutput : CommandOutput {
        private val messages = mutableListOf<Component>()

        override fun send(message: Component) {
            messages += message
        }

        fun plain(): List<String> = messages.map { it.toPlainText() }

        fun joined(): String = plain().joinToString("\n")
    }

    private class FakeHost(
        private val reloadError: Exception? = null,
    ) : CommandHost {
        var reloadCount = 0
        var kickedPlayer: String? = null
        var playerCount = 3

        override val displayName: String = "PlumBot Test"

        override fun reloadPlugin() {
            reloadCount++
            reloadError?.let { throw it }
        }

        override fun onlinePlayerCount(): Int = playerCount

        override fun kickPlayer(playerName: String) {
            kickedPlayer = playerName
        }
    }

    private fun run(host: CommandHost, vararg args: String): RecordingOutput {
        val output = RecordingOutput()
        PlumBotCommandHandler(host).execute(output, args.toList())
        return output
    }

    @Test
    fun `no args prints the help with every subcommand`() {
        val output = run(FakeHost())
        val text = output.joined()
        assertTrue(text.contains("PlumBot 指令帮助"), text)
        assertTrue(text.contains("/plumbot reload"), text)
        assertTrue(text.contains("/plumbot status"), text)
        assertTrue(text.contains("/plumbot bind query"), text)
        assertTrue(text.contains("/plumbot render doctor"), text)
        assertTrue(text.contains("/plumbot doctor echo"), text)
        assertTrue(text.contains("/plumbot render stats"), text)
        assertTrue(text.contains("/plumbot render reset"), text)
        assertEquals(11, output.plain().size, "帮助应为 1 行标题 + 10 行指令")
    }

    @Test
    fun `reload reports success through the host`() {
        val host = FakeHost()
        val output = run(host, "reload")
        assertEquals(1, host.reloadCount)
        assertTrue(output.joined().contains("成功重载"), output.joined())
    }

    @Test
    fun `reload failure becomes an error message instead of an exception`() {
        val host = FakeHost(reloadError = IllegalStateException("boom"))
        val output = run(host, "reload")
        assertTrue(output.joined().contains("重载失败: boom"), output.joined())
    }

    @Test
    fun `status shows the host display name and live player count`() {
        val output = run(FakeHost(), "status")
        val text = output.joined()
        assertTrue(text.contains("[PlumBot Test 运行状态]"), text)
        assertTrue(text.contains("在线玩家数: 3"), text)
    }

    @Test
    fun `render without a platform service reports the gap instead of failing`() {
        val output = run(FakeHost(), "render", "doctor")
        assertTrue(output.joined().contains("未接入渲染诊断"), output.joined())
    }

    @Test
    fun `doctor echo prints platform diagnostics without dispatching commands`() {
        val output = RecordingOutput()
        PlumBotCommandHandler(FakeHost(), null) { listOf("候选通道: paper_feedback -> console", "最近一次: 本会话尚未执行") }
            .execute(output, listOf("doctor", "echo"))
        val text = output.joined()
        assertTrue(text.contains("远程控制台回显诊断"), text)
        assertTrue(text.contains("候选通道: paper_feedback -> console"), text)
        assertTrue(text.contains("探测只读"), text)
    }

    @Test
    fun `doctor defaults to render diagnosis`() {
        val output = run(FakeHost(), "doctor")
        assertTrue(output.joined().contains("未接入渲染诊断"), output.joined())
    }

    @Test
    fun `unknown subcommand is rejected with guidance`() {
        val output = run(FakeHost(), "nope")
        assertTrue(output.joined().contains("未知子命令"), output.joined())
    }

    @Test
    fun `bind usage is printed before touching the database`() {
        val output = run(FakeHost(), "bind")
        assertTrue(output.joined().contains("用法: /plumbot bind"), output.joined())
    }

    @Test
    fun `suggestions cover top level and bind subcommands`() {
        val handler = PlumBotCommandHandler(FakeHost())
        assertEquals(listOf("reload", "status", "bind", "render", "doctor"), handler.suggest(emptyList()))
        assertEquals(listOf("status"), handler.suggest(listOf("st")))
        assertEquals(listOf("query", "add", "remove"), handler.suggest(listOf("bind", "")))
        assertEquals(listOf("doctor", "install", "stats", "reset"), handler.suggest(listOf("render", "")))
        assertEquals(listOf("install"), handler.suggest(listOf("render", "ins")))
        assertEquals(listOf("render", "echo"), handler.suggest(listOf("doctor", "")))
        assertEquals(listOf("echo"), handler.suggest(listOf("doctor", "ec")))
        assertEquals(emptyList(), handler.suggest(listOf("status", "")))
    }
}
