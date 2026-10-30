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

package me.regadpole.plumbot.config

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `messages.yml` → [Messages] 的绑定测试：用真实 YAML 钉住三个约定——字段名 = 配置键、
 * String/List 两种形态都能写入、缺失键保留默认值。绑定走 Java 字段反射而非 `Messages::class.members`，
 * 因为 kotlin-reflect 的实现类会被产物最小化裁掉。
 */
class MessagesBinderTest {

    private val prefixBackup = Messages.prefix
    private val helpBackup = Messages.help
    private val loadBackup = Messages.load

    @AfterTest
    fun restoreMessages() {
        Messages.prefix = prefixBackup
        Messages.help = helpBackup
        Messages.load = loadBackup
    }

    @Test
    fun `binds string and list properties and keeps defaults for missing keys`() {
        val dir = createTempDirectory("plumbot-messages")
        dir.resolve("messages.yml").writeText(
            """
            prefix: "<white>[<aqua>PlumBot<white>]<reset> "
            help:
              - "line-a"
              - "line-b"
            """.trimIndent() + "\n",
        )
        val conf = YamlConfigurator.createConfig(dir, "messages.yml")!!
        Messages.prefix = "<missing:prefix>"
        Messages.help = emptyList()
        Messages.load = "<missing:load>"

        MessagesBinder.apply(conf)

        assertEquals("<white>[<aqua>PlumBot<white>]<reset> ", Messages.prefix)
        assertEquals(listOf("line-a", "line-b"), Messages.help)
        assertEquals("<missing:load>", Messages.load)
    }
}
