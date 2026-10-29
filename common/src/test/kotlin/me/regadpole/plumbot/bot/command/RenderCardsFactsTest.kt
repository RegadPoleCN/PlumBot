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

import me.regadpole.plumbot.config.Messages
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 卡片「正文 / data 同源」回归测试。
 *
 * 图片版由模板读 `data`，文字版只拿 `card.text`（[RenderCards.plain]）。`bindTime`、绑定
 * 上限、名单、控制台状态只写进 `data` 时，同一条指令的图文就各说一半。这里钉住三件事：
 * 事实要补进正文、正文已有同一事实时不重复、缺值时不多出一行空话。
 */
class RenderCardsFactsTest {

    private val backups = linkedMapOf(
        "bindTimeLine" to Messages.bindTimeLine,
        "bindLimitLine" to Messages.bindLimitLine,
        "bindPlayersLine" to Messages.bindPlayersLine,
        "consoleMetaLine" to Messages.consoleMetaLine
    )

    @BeforeTest
    fun installMessages() {
        Messages.bindTimeLine = "绑定时间: %bind_time%"
        Messages.bindLimitLine = "可绑定上限: %limit%"
        Messages.bindPlayersLine = "当前绑定: %players%"
        Messages.consoleMetaLine = "状态: %state% · 通道: %channel%"
    }

    @AfterTest
    fun restoreMessages() {
        Messages.bindTimeLine = backups.getValue("bindTimeLine")
        Messages.bindLimitLine = backups.getValue("bindLimitLine")
        Messages.bindPlayersLine = backups.getValue("bindPlayersLine")
        Messages.consoleMetaLine = backups.getValue("consoleMetaLine")
    }

    @Test
    fun whitelistAppendsBindTimeToText() {
        val card = RenderCards.whitelist(
            text = "玩家 Steve 已绑定",
            title = "白名单",
            playerName = "Steve",
            qq = "123456",
            bindTime = "2026-10-20 13:02:39",
            avatarUrl = null,
            originId = null,
            originName = null,
            bindCount = 1,
            current = listOf("Steve")
        )
        assertEquals("2026-10-20 13:02:39", card.data["bindTime"])
        val projected = RenderCards.plain(card)
        assertTrue(projected.contains("绑定时间: 2026-10-20 13:02:39"), projected)
    }

    @Test
    fun whitelistDoesNotDuplicateBindTimeAlreadyInText() {
        val card = RenderCards.whitelist(
            text = "玩家 Steve 已绑定\n绑定时间: 2026-10-20 13:02:39",
            title = "白名单",
            playerName = "Steve",
            qq = "123456",
            bindTime = "2026-10-20 13:02:39",
            avatarUrl = null,
            originId = null,
            originName = null,
            bindCount = 1,
            current = listOf("Steve")
        )
        val projected = RenderCards.plain(card)
        assertEquals(1, projected.split("绑定时间").size - 1, projected)
    }

    @Test
    fun whitelistSkipsBlankBindTime() {
        val card = RenderCards.whitelist(
            text = "玩家 Steve 已绑定",
            title = "白名单",
            playerName = "Steve",
            qq = "123456",
            bindTime = "  ",
            avatarUrl = null,
            originId = null,
            originName = null,
            bindCount = 1,
            current = emptyList()
        )
        assertFalse(RenderCards.plain(card).contains("绑定时间"))
    }

    @Test
    fun bindListAppendsLimitAndPlayers() {
        val card = RenderCards.bindList(
            text = "你的绑定列表",
            title = "绑定",
            userId = "123456",
            players = listOf("Steve", "Alex"),
            limit = 4
        )
        val projected = RenderCards.plain(card)
        assertTrue(projected.contains("可绑定上限: 4"), projected)
        assertTrue(projected.contains("当前绑定: Steve, Alex"), projected)
    }

    @Test
    fun bindListDoesNotDuplicatePlayerListAlreadyInText() {
        val card = RenderCards.bindList(
            text = "当前绑定: Steve, Alex",
            title = "绑定",
            userId = "123456",
            players = listOf("Steve", "Alex"),
            limit = 4
        )
        val projected = RenderCards.plain(card)
        assertEquals(1, projected.split("当前绑定").size - 1, projected)
    }

    @Test
    fun bindListSkipsPlayerLineWhenEmpty() {
        val card = RenderCards.bindList(
            text = "你还没有绑定任何账号",
            title = "绑定",
            userId = "123456",
            players = emptyList(),
            limit = 4
        )
        val projected = RenderCards.plain(card)
        assertFalse(projected.contains("当前绑定"), projected)
    }

    @Test
    fun consoleAppendsStateAndChannel() {
        val card = RenderCards.console(
            text = "There are 0 of a max of 20 players online",
            title = "控制台执行回显",
            state = "CAPTURED",
            channel = "paper_feedback",
            code = null,
            durationMs = 12L
        )
        assertTrue(RenderCards.plain(card).contains("状态: CAPTURED · 通道: paper_feedback"))
    }

    @Test
    fun consoleUsesDashWhenChannelUnknown() {
        val card = RenderCards.console(
            text = "执行失败",
            title = "控制台执行回显",
            state = "FAILED",
            channel = null,
            code = "ECHO_DISPATCH_FALSE",
            durationMs = 0L
        )
        assertTrue(RenderCards.plain(card).contains("状态: FAILED · 通道: -"))
    }
}
