// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

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
