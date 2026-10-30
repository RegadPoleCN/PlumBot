// SPDX-FileCopyrightText: 2026 RegadPoleCN
// SPDX-License-Identifier: AGPL-3.0-or-later

package me.regadpole.plumbot.internal

import me.regadpole.plumbot.api.bot.BotFactory
import me.regadpole.plumbot.api.platform.PlatformContext
import me.regadpole.plumbot.bot.BotProvider
import me.regadpole.plumbot.testing.FakeBot
import me.regadpole.plumbot.testing.FakePlatformContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 适配器类别上报：只上报**实际在跑的**适配器类型，且不含任何标识信息（上报面越小越好）。
 */
class AdapterTelemetryTest {

    @AfterTest
    fun cleanup() {
        BotProvider.unloadBot()
        BotProvider.clearFactories()
    }

    @Test
    fun `reports unknown while no adapter is loaded`() {
        assertEquals("unknown", AdapterTelemetry.current())
    }

    @Test
    fun `reports the loaded adapter type in lower case`() {
        val context = FakePlatformContext()
        val bot = FakeBot(context, type = "MiraiMC")
        BotProvider.registerFactory(object : BotFactory {
            override val metadata = bot.metadata
            override fun create(context: PlatformContext) = bot
        })

        BotProvider.loadBot(context, bot.metadata.type)

        assertEquals("miraimc", AdapterTelemetry.current(), "上报的是实际加载的适配器类型（小写归一）")
    }
}
