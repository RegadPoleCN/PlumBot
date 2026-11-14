// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.bot.command.whitelist

import me.regadpole.plumbot.bot.command.BotCommandService
import me.regadpole.plumbot.config.Messages
import me.regadpole.plumbot.database.DatabaseProvider
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import me.regadpole.plumbot.testing.RecordingDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `id:` 查询命中脏数据（绑定的 QQ 号不是数字）时的行为。
 *
 * 历史缺陷：脏数据走的是“用法错误”提示，管理员会以为是自己命令打错了；
 * 实际上这是数据库里存在手工改坏的记录，需要的是“去修库”的信号 + 服务端 WARN 现场。
 */
class BindQueryDataAnomalyTest {

    private val groupId = 100L
    private val adminId = 2001L

    private lateinit var database: RecordingDatabase
    private lateinit var context: FakePlatformContext
    private lateinit var bot: FakeBot

    @BeforeTest
    fun setUp() {
        database = RecordingDatabase()
        DatabaseProvider.start(database)
        context = FakePlatformContext(mapOf("admins" to listOf(adminId)))
        bot = FakeBot(context)
        bot.markOnlineForTest()
    }

    @AfterTest
    fun tearDown() {
        DatabaseProvider.shutdown()
    }

    @Test
    fun `非数字绑定记录回复数据异常提示并留WARN现场`() {
        database.addBind("not-a-number", "Steve")

        BindQueryCommand(BotCommandService(context, bot)).execute("id: Steve", groupId, adminId)

        // 测试未加载 messages.yml，断言“模板 + 替换结果”而不是写死的文案。
        assertEquals(
            listOf(Messages.bindDataInvalid.replace("%player%", "Steve")),
            bot.sentMessages,
            "应回复数据异常专用提示"
        )
        assertTrue(
            !bot.sentMessages.any { it.contains(Messages.wrongUsage) },
            "不得再回用法提示（那会误导管理员去检查命令拼写）"
        )
        assertTrue(
            context.logs.any { it.startsWith("WARN") && it.contains("Steve") && it.contains("not-a-number") },
            "应留下含玩家名与原始值的 WARN 现场：${context.logs}"
        )
    }

    @Test
    fun `数字绑定记录仍走正常查询回复`() {
        database.addBind("3003", "Alex")

        BindQueryCommand(BotCommandService(context, bot)).execute("id: Alex", groupId, adminId)

        // 测试未加载 messages.yml：模板是占位符本身，占位符不含变量所以替换不改变它。
        // 这里只断言"走了正常模板通道"，替换正确性由 messages.yml 加载的集成路径覆盖。
        assertEquals(
            listOf(Messages.adminQueryIdBind),
            bot.sentMessages,
            "数字记录应走正常查询模板，而不是异常/用法分支"
        )
        // 环境本身会发渲染降级 WARN（无渲染引擎），只断言不出现"数据异常"那条。
        assertTrue(
            context.logs.none { it.contains("非数字绑定记录") },
            "正常路径不应产生数据异常告警"
        )
    }
}
