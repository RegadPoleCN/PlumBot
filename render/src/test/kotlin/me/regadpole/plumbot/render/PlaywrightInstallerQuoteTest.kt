// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.render

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 手动安装命令的 cmd.exe 引号转义契约：cmd 不识别反斜杠转义，
 * 内嵌引号必须双写，否则参数会碎裂。
 */
class PlaywrightInstallerQuoteTest {

    @Test
    fun `cmdQuote 包裹空格参数`() {
        assertEquals("\"a b\"", PlaywrightInstaller.cmdQuote("a b"))
    }

    @Test
    fun `cmdQuote 按 cmd 语法双写内嵌引号`() {
        assertEquals("\"he said \"\"hi\"\"\"", PlaywrightInstaller.cmdQuote("he said \"hi\""))
    }
}